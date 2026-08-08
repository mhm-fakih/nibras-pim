package com.nibras.pim.service;

import com.nibras.pim.domain.Planner;
import com.nibras.pim.repo.PlannerRepository;
import com.nibras.pim.service.recurrence.RecurrenceSteps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Recurring planner events — habits that happen at a fixed time of day.
 *
 * <p><b>A series is a rule, not a chain.</b> Task recurrence materialises one occurrence at a time
 * and clears the source's flag, so exactly one future instance ever exists; that suits a to-do and
 * fails a 07:00 habit, where you want to see next Tuesday and to know what you missed. Here the
 * rule lives on one row — the <em>master</em>, whose own {@code startDate} is occurrence 0 — and
 * every later occurrence is computed on demand inside whatever window the calendar is drawing. A
 * real row appears only when an occurrence is <em>touched</em>: opened, moved, or given a note.
 *
 * <p>Three kinds of row exist, distinguished without ambiguity:
 * <ul>
 *   <li><b>master</b> — {@code recurring = true}, {@code seriesId = null}. Holds the rule and is
 *       itself the first occurrence.</li>
 *   <li><b>materialised occurrence</b> — {@code seriesId = master.id}, {@code recurrenceId} = the
 *       slot's ORIGINAL start, {@code recurring = false}. An ordinary planner in every other way,
 *       so it edits, syncs, exports and searches like one.</li>
 *   <li><b>plain planner</b> — neither.</li>
 * </ul>
 *
 * <p>Storing {@code recurrenceId} separately from {@code startDate} is what makes "drag this one
 * occurrence to 09:00" work: the row moves, but it still stands for the 07:00 slot, so expansion
 * suppresses the virtual occurrence there instead of drawing a ghost at 07:00 and a duplicate at
 * 09:00.
 *
 * <p>Deleting an occurrence that was never materialised has nothing to soft-delete, so the master
 * carries an EXDATE list ({@code recurrenceSkips}) of slots to omit.
 *
 * <p>Times are naive local ({@code LocalDateTime}), and that is deliberate: expanding a rule gives
 * 07:00 wall-clock every day across a DST boundary for free, where pre-computed absolute instants
 * would drift by an hour twice a year.
 */
@Service
public class PlannerSeriesService {

    private static final Logger log = LoggerFactory.getLogger(PlannerSeriesService.class);

    /**
     * Most occurrences one series may contribute to one window. A month of a daily habit is ~31, so
     * this is only ever reached by a pathological rule (a CUSTOM cron firing every minute); it
     * bounds the response instead of letting one bad rule wedge the calendar.
     */
    private static final int MAX_OCCURRENCES_PER_WINDOW = 750;

    /** Cap on steps walked to reach the window when the rule has no closed form (CUSTOM cron). */
    private static final int MAX_FASTFORWARD_STEPS = 10_000;

    /**
     * "This series contributes nothing here." A distinct sentinel rather than Integer.MAX_VALUE,
     * which as a loop start would ask RecurrenceSteps for the two-billionth occurrence and run the
     * arithmetic off the end of the calendar.
     */
    private static final int NO_OCCURRENCE = -1;

    private final PlannerRepository planners;

    public PlannerSeriesService(PlannerRepository planners) {
        this.planners = planners;
    }

    /**
     * One computed, not-yet-existing occurrence of a series. Its {@code recurrenceId} — the slot it
     * stands for — is its {@code start}: they only diverge once the occurrence has been materialised
     * and moved, and by then it is a row, not one of these.
     */
    public record Occurrence(Planner master, LocalDateTime start, LocalDateTime end) {
        public LocalDateTime recurrenceId() {
            return start;
        }
    }

    // ---------------------------------------------------------------- expansion

    /**
     * Every virtual occurrence falling in {@code [from, to]}, across all series.
     *
     * <p>Excluded, because they are already real rows the caller lists by other means: occurrence 0
     * (the master itself) and any occurrence that has been materialised. Also excluded: anything on
     * the master's skip list.
     */
    public List<Occurrence> expand(LocalDateTime from, LocalDateTime to) {
        List<Planner> masters = planners.findSeriesMasters();
        if (masters.isEmpty()) {
            return List.of();
        }
        // One query for every series' exceptions, keyed by (seriesId, recurrenceId). Not limited to
        // the window — see findMaterialisedOccurrences.
        Set<String> taken = planners
                .findMaterialisedOccurrences(masters.stream().map(Planner::getId).toList())
                .stream()
                .filter(p -> p.getRecurrenceId() != null)
                .map(p -> slotKey(p.getSeriesId(), p.getRecurrenceId()))
                .collect(Collectors.toSet());

        List<Occurrence> out = new ArrayList<>();
        for (Planner master : masters) {
            out.addAll(expandOne(master, from, to, taken));
        }
        return out;
    }

    private List<Occurrence> expandOne(Planner master, LocalDateTime from, LocalDateTime to, Set<String> taken) {
        LocalDateTime anchor = master.getStartDate();
        if (anchor == null || !RecurrenceSteps.usable(anchor, master.getRecurrencePreset(),
                master.getRecurrenceInterval(), master.getRecurrenceCron())) {
            // A rule that can't move time forward would expand to an infinite run of the same
            // instant. The legacy `recurring` varchar really does hold stray values.
            return List.of();
        }
        Duration span = master.getEndDate() == null
                ? Duration.ZERO
                : Duration.between(anchor, master.getEndDate());
        LocalDateTime horizon = master.getRecurrenceUntil() != null && master.getRecurrenceUntil().isBefore(to)
                ? master.getRecurrenceUntil()
                : to;
        // recurrenceCount counts the anchor, so it caps the INDEX at count - 1.
        int maxIndex = master.getRecurrenceCount() == null ? Integer.MAX_VALUE : master.getRecurrenceCount() - 1;
        Set<LocalDateTime> skips = skipsOf(master);

        int startIndex = firstIndexAtOrAfter(master, anchor, from);
        if (startIndex == NO_OCCURRENCE) {
            return List.of();   // couldn't reach the window at all — see walkToWindow
        }

        List<Occurrence> out = new ArrayList<>();
        for (int i = startIndex; i <= maxIndex; i++) {
            LocalDateTime start = RecurrenceSteps.nth(anchor, i, master.getRecurrencePreset(),
                    master.getRecurrenceInterval(), master.getRecurrenceCron());
            if (start == null || start.isAfter(horizon)) {
                break;
            }
            if (out.size() >= MAX_OCCURRENCES_PER_WINDOW) {
                log.warn("Planner series {} produced more than {} occurrences in {}..{} — truncated. "
                                + "Check its recurrence rule (preset={}, interval={}, cron={}).",
                        master.getId(), MAX_OCCURRENCES_PER_WINDOW, from, to,
                        master.getRecurrencePreset(), master.getRecurrenceInterval(), master.getRecurrenceCron());
                break;
            }
            if (i == 0 || start.isBefore(from)) {
                continue;   // occurrence 0 is the master row itself; earlier ones are out of window
            }
            if (skips.contains(start) || taken.contains(slotKey(master.getId(), start))) {
                continue;
            }
            out.add(new Occurrence(master, start, start.plus(span)));
        }
        return out;
    }

    /**
     * The first occurrence index at or after {@code from}, jumped to arithmetically rather than by
     * stepping — a daily habit anchored five years ago must not cost 1800 iterations on every
     * calendar draw. CUSTOM has no closed form, so it walks (bounded).
     *
     * <p>Always returns at least 1: index 0 is the master row, which the caller lists separately.
     * The estimate is deliberately allowed to undershoot — {@link #expandOne} skips anything still
     * before the window — because overshooting would silently drop real occurrences.
     */
    private int firstIndexAtOrAfter(Planner master, LocalDateTime anchor, LocalDateTime from) {
        if (!from.isAfter(anchor)) {
            return 1;
        }
        int step = master.getRecurrenceInterval() == null || master.getRecurrenceInterval() < 1
                ? 1 : master.getRecurrenceInterval();
        String preset = master.getRecurrencePreset() == null ? "" : master.getRecurrencePreset().toUpperCase();
        long units = switch (preset) {
            case "DAILY" -> ChronoUnit.DAYS.between(anchor, from);
            case "WEEKLY" -> ChronoUnit.WEEKS.between(anchor, from);
            case "MONTHLY" -> ChronoUnit.MONTHS.between(anchor, from);
            case "YEARLY" -> ChronoUnit.YEARS.between(anchor, from);
            default -> -1;   // CUSTOM / unknown: fall through to walking
        };
        if (units < 0) {
            return walkToWindow(master, anchor, from);
        }
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE - 1L, units / step));
    }

    /** Step-by-step fast-forward for rules with no closed form. Bounded; see MAX_FASTFORWARD_STEPS. */
    private int walkToWindow(Planner master, LocalDateTime anchor, LocalDateTime from) {
        LocalDateTime at = anchor;
        for (int i = 0; i < MAX_FASTFORWARD_STEPS; i++) {
            LocalDateTime moved = RecurrenceSteps.next(at, master.getRecurrencePreset(),
                    master.getRecurrenceInterval(), master.getRecurrenceCron());
            if (moved == null || !moved.isAfter(at)) {
                return NO_OCCURRENCE;   // unusable — expandOne's guard will have caught this
            }
            at = moved;
            if (!at.isBefore(from)) {
                return i + 1;
            }
        }
        log.warn("Planner series {} needed more than {} steps to reach {} — not expanded.",
                master.getId(), MAX_FASTFORWARD_STEPS, from);
        return NO_OCCURRENCE;
    }

    // ---------------------------------------------------------------- materialising

    /**
     * Turns one occurrence into a real Planner row, copying the master's content and placing it at
     * the occurrence's own times. Idempotent: touching the same slot twice returns the row created
     * the first time, so a double-click or a retried request can't produce two.
     *
     * <p>The copy is deliberately NOT itself recurring — the rule stays on the master. Otherwise
     * every materialised occurrence would become a second series generating its own occurrences.
     */
    @Transactional
    public Planner materialise(Long seriesId, LocalDateTime recurrenceId) {
        Planner master = planners.findById(seriesId)
                .orElseThrow(() -> new IllegalArgumentException("No planner series " + seriesId));
        Optional<Planner> existing = planners.findFirstBySeriesIdAndRecurrenceId(seriesId, recurrenceId);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (!occurs(master, recurrenceId)) {
            throw new IllegalArgumentException(
                    "Planner series " + seriesId + " has no occurrence at " + recurrenceId);
        }

        Duration span = master.getEndDate() == null
                ? Duration.ZERO
                : Duration.between(master.getStartDate(), master.getEndDate());

        Planner p = new Planner();
        p.setSummary(master.getSummary());
        p.setDescription(master.getDescription());
        p.setNotes(master.getNotes());
        // `reality` is deliberately NOT copied: it records what actually happened on the master's
        // own day, and carrying it into a fresh occurrence would be a lie.
        p.setPriority(master.getPriority());
        p.setIsPrivate(master.getIsPrivate());
        p.setType(master.getType());
        p.setStatus(master.getStatus());
        p.setDepartment(master.getDepartment());
        p.setCourse(master.getCourse());
        p.setTask(master.getTask());
        p.setGoal(master.getGoal());
        p.setBook(master.getBook());
        p.setLanguage(master.getLanguage());
        p.setTags(new LinkedHashSet<>(master.getTags()));

        p.setStartDate(recurrenceId);
        p.setEndDate(master.getEndDate() == null ? null : recurrenceId.plus(span));
        p.setSeriesId(seriesId);
        p.setRecurrenceId(recurrenceId);
        p.setRecurring(false);

        return planners.saveAndFlush(p);
    }

    /**
     * Removes one occurrence from the series.
     *
     * <p>Three cases, because "delete this one" means something different for each kind of row:
     * the master IS occurrence 0, so removing it would take the rule with it — instead the anchor
     * moves to the next surviving occurrence. A materialised occurrence is soft-deleted AND added
     * to the skip list, or expansion would helpfully draw it again as a virtual occurrence. A
     * purely virtual occurrence has nothing to delete, so only the skip list changes.
     */
    @Transactional
    public void skip(Long seriesId, LocalDateTime recurrenceId) {
        Planner master = planners.findById(seriesId)
                .orElseThrow(() -> new IllegalArgumentException("No planner series " + seriesId));

        planners.findFirstBySeriesIdAndRecurrenceId(seriesId, recurrenceId).ifPresent(row -> {
            row.setDeletedOn(LocalDateTime.now());
            planners.save(row);
        });

        if (recurrenceId.equals(master.getStartDate())) {
            advanceAnchor(master);
        } else {
            addSkip(master, recurrenceId);
        }
        planners.saveAndFlush(master);
    }

    /**
     * Moves the master onto its next surviving occurrence, preserving the event's duration. Used
     * when the first occurrence is deleted: the rule has to live somewhere, and the master row is
     * where it lives.
     *
     * <p>When nothing survives — a count of 1, or an `until` before the next step — the series is
     * simply switched off rather than left pointing at an occurrence that can't happen.
     */
    private void advanceAnchor(Planner master) {
        LocalDateTime anchor = master.getStartDate();
        Duration span = master.getEndDate() == null
                ? Duration.ZERO
                : Duration.between(anchor, master.getEndDate());
        Set<LocalDateTime> skips = skipsOf(master);
        int maxIndex = master.getRecurrenceCount() == null ? Integer.MAX_VALUE : master.getRecurrenceCount() - 1;

        for (int i = 1; i <= maxIndex && i <= MAX_OCCURRENCES_PER_WINDOW; i++) {
            LocalDateTime candidate = RecurrenceSteps.nth(anchor, i, master.getRecurrencePreset(),
                    master.getRecurrenceInterval(), master.getRecurrenceCron());
            if (candidate == null
                    || (master.getRecurrenceUntil() != null && candidate.isAfter(master.getRecurrenceUntil()))) {
                break;
            }
            if (skips.contains(candidate)) {
                continue;
            }
            master.setStartDate(candidate);
            if (master.getEndDate() != null) {
                master.setEndDate(candidate.plus(span));
            }
            // The dropped occurrences are now before the anchor and can never be produced again, so
            // the skip list can shed them and `count` has one fewer to go.
            master.setRecurrenceSkips(renderSkips(
                    skips.stream().filter(s -> s.isAfter(candidate)).collect(Collectors.toCollection(LinkedHashSet::new))));
            if (master.getRecurrenceCount() != null) {
                master.setRecurrenceCount(Math.max(1, master.getRecurrenceCount() - i));
            }
            return;
        }
        master.setRecurring(false);
    }

    /** True when {@code at} is an occurrence of this series (and not skipped). */
    private boolean occurs(Planner master, LocalDateTime at) {
        LocalDateTime anchor = master.getStartDate();
        if (anchor == null || at == null || at.isBefore(anchor) || skipsOf(master).contains(at)) {
            return false;
        }
        if (master.getRecurrenceUntil() != null && at.isAfter(master.getRecurrenceUntil())) {
            return false;
        }
        int maxIndex = master.getRecurrenceCount() == null ? Integer.MAX_VALUE : master.getRecurrenceCount() - 1;
        int i = firstIndexAtOrAfter(master, anchor, at);
        if (i == NO_OCCURRENCE) {
            return false;
        }
        // The index estimate may undershoot (see firstIndexAtOrAfter), so scan forward a little
        // rather than testing one candidate. Month clamping makes an exact inverse unreliable.
        for (int probe = Math.max(0, i - 2); probe <= maxIndex && probe <= i + 2; probe++) {
            LocalDateTime candidate = RecurrenceSteps.nth(anchor, probe, master.getRecurrencePreset(),
                    master.getRecurrenceInterval(), master.getRecurrenceCron());
            if (candidate == null) {
                return false;
            }
            if (candidate.equals(at)) {
                return true;
            }
            if (candidate.isAfter(at)) {
                return false;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- skip-list storage

    /**
     * The skip list, parsed. Stored as newline-separated ISO local datetimes in a longtext rather
     * than a child table: it is a short list read only alongside its own row, and a table would add
     * a join to every calendar draw for no gain.
     */
    private Set<LocalDateTime> skipsOf(Planner master) {
        String raw = master.getRecurrenceSkips();
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<LocalDateTime> out = new LinkedHashSet<>();
        for (String line : raw.split("\\R")) {
            String s = line.trim();
            if (s.isEmpty()) {
                continue;
            }
            try {
                out.add(LocalDateTime.parse(s));
            } catch (RuntimeException e) {
                log.warn("Planner series {} has an unparseable skip entry {} — ignored.", master.getId(), s);
            }
        }
        return out;
    }

    private void addSkip(Planner master, LocalDateTime recurrenceId) {
        Set<LocalDateTime> skips = new LinkedHashSet<>(skipsOf(master));
        skips.add(recurrenceId);
        master.setRecurrenceSkips(renderSkips(skips));
    }

    private String renderSkips(Set<LocalDateTime> skips) {
        return skips.isEmpty() ? null
                : skips.stream().sorted().map(LocalDateTime::toString).collect(Collectors.joining("\n"));
    }

    private static String slotKey(Long seriesId, LocalDateTime recurrenceId) {
        return seriesId + "@" + recurrenceId;
    }
}
