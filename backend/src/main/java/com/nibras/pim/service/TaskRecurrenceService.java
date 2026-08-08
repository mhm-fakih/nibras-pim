package com.nibras.pim.service;

import com.nibras.pim.domain.Task;
import com.nibras.pim.repo.TaskRepository;
import com.nibras.pim.service.recurrence.RecurrenceSteps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Recurrence for tasks. Two trigger paths share this service: {@link TaskService#update} calls
 * {@link #spawnNext} directly when a completion-triggered recurring task is marked done;
 * {@link com.nibras.pim.scheduling.RecurrenceScheduler} calls {@link #materializeDueFixedScheduleTasks}
 * once a day for tasks whose recurrence is set to "fixed schedule" (recurs by date regardless of
 * completion). Either way, recurrence "moves" to the newly spawned task — the source task's
 * `recurring` flag is cleared so it can never spawn a second time.
 */
@Service
public class TaskRecurrenceService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(TaskRecurrenceService.class);

    private final TaskRepository tasks;

    public TaskRecurrenceService(TaskRepository tasks) {
        this.tasks = tasks;
    }

    /**
     * Advances `from` by one recurrence step per the given preset/interval/cron. Delegates to
     * {@link RecurrenceSteps}, which planner recurrence also uses — one definition of what
     * "monthly" means, so the two models can't drift apart.
     */
    public LocalDateTime computeNext(LocalDateTime from, String preset, Integer interval, String cron) {
        return RecurrenceSteps.next(from, preset, interval, cron);
    }

    /**
     * How many recurrence steps we'll walk to catch up an overdue task before giving up. Generous
     * enough for a daily task abandoned for years, small enough that a pathological rule can't spin.
     */
    private static final int MAX_CATCHUP_STEPS = 4000;

    /**
     * The next occurrence after {@code anchor}, or {@code null} if the rule can't move time forward.
     *
     * <p><b>Fixed-schedule tasks catch up; completion-triggered ones don't.</b> The nightly sweep
     * spawns anything whose due date has passed, so for those a single step from an anchor months
     * overdue lands on a date that is STILL in the past — immediately due again, spawning another
     * copy the next night, one per day until the chain crawls back to the present. That is the
     * "monthly task repeats every day" report, and why fixed-schedule walks on to the first
     * occurrence in the future.
     *
     * <p>Completion-triggered recurrence deliberately keeps the single step: finishing a weekly task
     * late should put the next one a week after the LAST due date (so it reads as already overdue,
     * which is true), not silently reschedule it into next week. Nothing loops there — it only fires
     * when a human marks something done.
     *
     * <p>{@code null} covers a rule that cannot advance at all (no preset, an unrecognised preset, a
     * blank CUSTOM cron): {@link #computeNext} answers those with the input unchanged, which would
     * mean spawning an exact copy on the same dates — every night, forever, for a fixed-schedule
     * task. The legacy `recurring` varchar really does hold nulls and stray values, so this is
     * reachable in real data.
     */
    private LocalDateTime nextOccurrenceAfter(LocalDateTime anchor, LocalDateTime now, Task source) {
        LocalDateTime next = computeNext(anchor, source.getRecurrencePreset(),
                source.getRecurrenceInterval(), source.getRecurrenceCron());
        if (next == null || !next.isAfter(anchor)) {
            return null;   // the rule doesn't move time forward — refuse rather than duplicate
        }
        if (!Boolean.TRUE.equals(source.getFixedSchedule())) {
            return next;   // completion-triggered: exactly one step, even if that's in the past
        }
        for (int i = 0; i < MAX_CATCHUP_STEPS && !next.isAfter(now); i++) {
            LocalDateTime step = computeNext(next, source.getRecurrencePreset(),
                    source.getRecurrenceInterval(), source.getRecurrenceCron());
            if (step == null || !step.isAfter(next)) {
                break;
            }
            next = step;
        }
        return next;
    }

    /**
     * Creates the next occurrence and closes out {@code source}. Returns {@code null} — spawning
     * nothing and leaving {@code source} untouched — when the recurrence rule can't produce a later
     * date (see {@link #nextOccurrenceAfter}).
     */
    @Transactional
    public Task spawnNext(Task source) {
        Task next = new Task();
        next.setSummary(source.getSummary());
        next.setName(source.getName());
        next.setDescription(source.getDescription());
        next.setNotes(source.getNotes());
        next.setDepartment(source.getDepartment());
        next.setCourse(source.getCourse());
        next.setGoal(source.getGoal());
        next.setContext(source.getContext());
        next.setPriority(source.getPriority());
        next.setBookmarked(source.getBookmarked());
        next.setUrgent(source.getUrgent());
        next.setTags(new LinkedHashSet<>(source.getTags()));

        // The rule moves forward with the new task; the source is closed out below.
        next.setRecurring(true);
        next.setRecurrencePreset(source.getRecurrencePreset());
        next.setRecurrenceInterval(source.getRecurrenceInterval());
        next.setRecurrenceCron(source.getRecurrenceCron());
        next.setFixedSchedule(source.getFixedSchedule());

        LocalDateTime anchor = source.getEndDate() != null ? source.getEndDate() : source.getStartDate();
        LocalDateTime nextAnchor = anchor == null
                ? null
                : nextOccurrenceAfter(anchor, LocalDateTime.now(), source);
        if (anchor != null && nextAnchor == null) {
            return null;   // unusable rule — see nextOccurrenceAfter
        }
        if (source.getStartDate() != null && source.getEndDate() != null) {
            // Preserve the original duration (e.g. a 2-day task stays a 2-day task).
            Duration span = Duration.between(source.getStartDate(), source.getEndDate());
            next.setEndDate(nextAnchor);
            next.setStartDate(nextAnchor.minus(span));
        } else if (source.getEndDate() != null) {
            next.setEndDate(nextAnchor);
        } else if (source.getStartDate() != null) {
            next.setStartDate(nextAnchor);
        }

        next = tasks.saveAndFlush(next);

        source.setRecurring(false);
        tasks.saveAndFlush(source);

        return next;
    }

    /**
     * Daily sweep for "fixed schedule" recurring tasks: spawns the next occurrence once the due
     * date (endDate, or startDate if there's no endDate) has passed, whether or not the current
     * occurrence was completed.
     */
    @Transactional
    public int materializeDueFixedScheduleTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<Task> due = tasks.findAll((root, query, cb) -> cb.and(
                cb.isTrue(root.get("recurring")),
                cb.isTrue(root.get("fixedSchedule")),
                cb.or(
                        cb.and(cb.isNotNull(root.get("endDate")),
                                cb.lessThanOrEqualTo(root.get("endDate"), now)),
                        cb.and(cb.isNull(root.get("endDate")), cb.isNotNull(root.get("startDate")),
                                cb.lessThanOrEqualTo(root.get("startDate"), now)))));
        int spawned = 0;
        for (Task t : due) {
            if (spawnNext(t) != null) {
                spawned++;
            } else {
                // Left as-is on purpose: it keeps `recurring` so a corrected rule takes effect, and
                // spawning nothing is strictly better than the copy-a-day this used to produce.
                log.warn("Task {} is due but its recurrence rule can't produce a later date "
                        + "(preset={}, interval={}, cron={}) — skipped.",
                        t.getId(), t.getRecurrencePreset(), t.getRecurrenceInterval(), t.getRecurrenceCron());
            }
        }
        return spawned;
    }
}
