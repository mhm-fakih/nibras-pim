package com.nibras.pim;

import com.nibras.pim.domain.Planner;
import com.nibras.pim.repo.PlannerRepository;
import com.nibras.pim.service.CalendarEventService;
import com.nibras.pim.service.PlannerSeriesService;
import com.nibras.pim.support.AuthTestSupport;
import com.nibras.pim.web.dto.CalendarEventDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recurring planner events: a rule expanded into occurrences at read time, materialised only when
 * touched. See {@link PlannerSeriesService}.
 *
 * <p>Every test scopes its assertions to its own series. The database is shared and persistent
 * (see {@link AuthTestSupport}) and expansion is global — an unscoped "how many events in this
 * window" would depend on every habit any previous run left behind.
 *
 * <p>Dates are pinned to a fixed year rather than relative to now, so a test can't drift into a
 * different month boundary depending on when it runs. 2027 is far enough ahead that no real data
 * sits there.
 */
class PlannerSeriesTests extends AuthTestSupport {

    /** A Thursday at 07:00, deliberately away from a month/DST edge. */
    private static final LocalDateTime ANCHOR = LocalDateTime.of(2027, 3, 4, 7, 0);

    private final String tag = "series-" + System.nanoTime();

    @Autowired
    PlannerSeriesService series;

    @Autowired
    PlannerRepository planners;

    @Autowired
    CalendarEventService calendar;

    private Planner dailyHabit(String suffix, LocalTime end) {
        Planner p = new Planner();
        p.setSummary(tag + suffix);
        p.setStartDate(ANCHOR);
        p.setEndDate(ANCHOR.toLocalDate().atTime(end));
        p.setRecurring(true);
        p.setRecurrencePreset("DAILY");
        p.setRecurrenceInterval(1);
        return planners.saveAndFlush(p);
    }

    /** This series' occurrences in the window, virtual ones only. */
    private List<PlannerSeriesService.Occurrence> expand(Planner master, LocalDateTime from, LocalDateTime to) {
        return series.expand(from, to).stream()
                .filter(o -> o.master().getId().equals(master.getId()))
                .toList();
    }

    @Test
    void aDailyHabitFillsTheWeekAtItsOwnTimeOfDay() {
        Planner master = dailyHabit("-daily", LocalTime.of(8, 0));

        List<PlannerSeriesService.Occurrence> out =
                expand(master, ANCHOR.toLocalDate().atStartOfDay(), ANCHOR.plusDays(6).with(LocalTime.MAX));

        // Six virtual occurrences: day 0 is the master row itself, which the calendar lists as a
        // real record — expanding it too would draw the same event twice.
        assertThat(out).hasSize(6);
        assertThat(out).allSatisfy(o -> {
            assertThat(o.start().toLocalTime()).isEqualTo(LocalTime.of(7, 0));
            assertThat(o.end().toLocalTime()).isEqualTo(LocalTime.of(8, 0));
        });
        assertThat(out.get(0).start()).isEqualTo(ANCHOR.plusDays(1));
        assertThat(out.get(5).start()).isEqualTo(ANCHOR.plusDays(6));
    }

    @Test
    void theSeriesCostsNoRowsUntilAnOccurrenceIsTouched() {
        Planner master = dailyHabit("-cheap", LocalTime.of(8, 0));
        long before = planners.count();

        expand(master, ANCHOR.toLocalDate().atStartOfDay(), ANCHOR.plusYears(1).with(LocalTime.MAX));

        // A year of a daily habit is 365 occurrences and exactly zero new rows. That is the whole
        // point of the model — the chain approach would have needed a row for each.
        assertThat(planners.count()).isEqualTo(before);
    }

    @Test
    void countAndUntilBoundTheSeries() {
        Planner counted = dailyHabit("-counted", LocalTime.of(8, 0));
        counted.setRecurrenceCount(3);            // the anchor plus two repeats
        planners.saveAndFlush(counted);

        Planner limited = dailyHabit("-until", LocalTime.of(8, 0));
        limited.setRecurrenceUntil(ANCHOR.plusDays(2));
        planners.saveAndFlush(limited);

        LocalDateTime from = ANCHOR.toLocalDate().atStartOfDay();
        LocalDateTime to = ANCHOR.plusDays(30).with(LocalTime.MAX);

        assertThat(expand(counted, from, to)).hasSize(2);
        assertThat(expand(limited, from, to))
                .extracting(PlannerSeriesService.Occurrence::start)
                .containsExactly(ANCHOR.plusDays(1), ANCHOR.plusDays(2));
    }

    @Test
    void materialisingIsIdempotentAndReplacesTheVirtualOccurrence() {
        Planner master = dailyHabit("-touch", LocalTime.of(8, 0));
        LocalDateTime slot = ANCHOR.plusDays(3);

        Planner first = series.materialise(master.getId(), slot);
        Planner again = series.materialise(master.getId(), slot);

        assertThat(again.getId()).isEqualTo(first.getId());   // a retry must not make a second row
        assertThat(first.getStartDate()).isEqualTo(slot);
        assertThat(first.getRecurring()).isFalse();           // the rule stays on the master only
        assertThat(first.getSeriesId()).isEqualTo(master.getId());

        assertThat(expand(master, ANCHOR.toLocalDate().atStartOfDay(), ANCHOR.plusDays(6).with(LocalTime.MAX)))
                .extracting(PlannerSeriesService.Occurrence::start)
                .doesNotContain(slot);
    }

    /**
     * The reason {@code recurrenceId} is stored separately from {@code startDate}. Moving an
     * occurrence must not leave a ghost behind at the slot it came from.
     */
    @Test
    void movingAMaterialisedOccurrenceLeavesNoGhostAtItsOriginalSlot() {
        Planner master = dailyHabit("-moved", LocalTime.of(8, 0));
        LocalDateTime slot = ANCHOR.plusDays(2);

        Planner row = series.materialise(master.getId(), slot);
        row.setStartDate(slot.withHour(15));     // dragged from 07:00 to 15:00
        row.setEndDate(slot.withHour(16));
        planners.saveAndFlush(row);

        List<PlannerSeriesService.Occurrence> out =
                expand(master, ANCHOR.toLocalDate().atStartOfDay(), ANCHOR.plusDays(6).with(LocalTime.MAX));

        assertThat(out).extracting(PlannerSeriesService.Occurrence::start).doesNotContain(slot);
        assertThat(row.getRecurrenceId()).isEqualTo(slot);   // still stands for the 07:00 slot
    }

    @Test
    void skippingAnOccurrenceRemovesItWithoutCreatingARow() {
        Planner master = dailyHabit("-skip", LocalTime.of(8, 0));
        LocalDateTime slot = ANCHOR.plusDays(2);
        long before = planners.count();

        series.skip(master.getId(), slot);

        assertThat(planners.count()).isEqualTo(before);
        assertThat(expand(master, ANCHOR.toLocalDate().atStartOfDay(), ANCHOR.plusDays(6).with(LocalTime.MAX)))
                .extracting(PlannerSeriesService.Occurrence::start)
                .doesNotContain(slot)
                .contains(ANCHOR.plusDays(1), ANCHOR.plusDays(3));
    }

    @Test
    void skippingAMaterialisedOccurrenceDoesNotResurrectItAsVirtual() {
        Planner master = dailyHabit("-skip-real", LocalTime.of(8, 0));
        LocalDateTime slot = ANCHOR.plusDays(2);
        series.materialise(master.getId(), slot);

        series.skip(master.getId(), slot);

        // Soft-deleting the row alone would put the slot back in play, and expansion would
        // helpfully draw the occurrence the user just deleted.
        assertThat(expand(master, ANCHOR.toLocalDate().atStartOfDay(), ANCHOR.plusDays(6).with(LocalTime.MAX)))
                .extracting(PlannerSeriesService.Occurrence::start)
                .doesNotContain(slot);
    }

    /** The master IS occurrence 0, so deleting it has to move the rule rather than destroy it. */
    @Test
    void skippingTheFirstOccurrenceMovesTheAnchorAndKeepsTheSeries() {
        Planner master = dailyHabit("-first", LocalTime.of(8, 0));

        series.skip(master.getId(), ANCHOR);

        Planner reloaded = planners.findById(master.getId()).orElseThrow();
        assertThat(reloaded.getRecurring()).isTrue();
        assertThat(reloaded.getStartDate()).isEqualTo(ANCHOR.plusDays(1));
        assertThat(reloaded.getEndDate()).isEqualTo(ANCHOR.plusDays(1).withHour(8));   // span kept
    }

    @Test
    void aSeriesOfOneSwitchesOffWhenItsOnlyOccurrenceIsSkipped() {
        Planner master = dailyHabit("-single", LocalTime.of(8, 0));
        master.setRecurrenceCount(1);
        planners.saveAndFlush(master);

        series.skip(master.getId(), ANCHOR);

        assertThat(planners.findById(master.getId()).orElseThrow().getRecurring()).isFalse();
    }

    @Test
    void aRuleThatCannotAdvanceExpandsToNothingInsteadOfLooping() {
        Planner master = dailyHabit("-broken", LocalTime.of(8, 0));
        master.setRecurrencePreset(null);   // the legacy varchar really does hold nulls
        planners.saveAndFlush(master);

        assertThat(expand(master, ANCHOR.toLocalDate().atStartOfDay(), ANCHOR.plusDays(30).with(LocalTime.MAX)))
                .isEmpty();
    }

    @Test
    void materialisingASlotTheSeriesDoesNotHaveIsRejected() {
        Planner master = dailyHabit("-bogus", LocalTime.of(8, 0));

        assertThatThrownBy(() -> series.materialise(master.getId(), ANCHOR.plusDays(1).plusMinutes(37)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A monthly habit anchored on the 31st. The step is always taken from the anchor, so a short
     * month clamps for that month only instead of dragging the whole series onto the 28th.
     */
    @Test
    void monthlyFromTheThirtyFirstClampsWithoutDraggingTheSeriesBackwards() {
        Planner master = new Planner();
        master.setSummary(tag + "-31st");
        master.setStartDate(LocalDateTime.of(2027, 1, 31, 7, 0));
        master.setEndDate(LocalDateTime.of(2027, 1, 31, 8, 0));
        master.setRecurring(true);
        master.setRecurrencePreset("MONTHLY");
        master.setRecurrenceInterval(1);
        planners.saveAndFlush(master);

        List<LocalDateTime> starts = expand(master,
                LocalDateTime.of(2027, 1, 1, 0, 0), LocalDateTime.of(2027, 4, 30, 23, 59))
                .stream().map(PlannerSeriesService.Occurrence::start).toList();

        assertThat(starts).containsExactly(
                LocalDateTime.of(2027, 2, 28, 7, 0),   // clamped
                LocalDateTime.of(2027, 3, 31, 7, 0),   // ...and back on the 31st, not the 28th
                LocalDateTime.of(2027, 4, 30, 7, 0));
    }

    /**
     * The occurrence endpoints over real HTTP.
     *
     * <p>Worth doing separately from the service tests because the risk here is the wire format, not
     * the logic: the client hands back the {@code recurrenceId} string the calendar gave it, and a
     * {@link LocalDateTime} that doesn't round-trip through Jackson byte-for-byte would silently
     * fail to match any slot — "materialise" would 400 on a slot the user can plainly see.
     */
    @Test
    void theOccurrenceEndpointsRoundTripARecurrenceIdOverHttp() throws Exception {
        Planner master = dailyHabit("-http", LocalTime.of(8, 0));

        // Exactly what the browser receives, serialised the same way.
        CalendarEventDto virtual = calendar
                .events(ANCHOR.toLocalDate().atStartOfDay(), ANCHOR.plusDays(3).with(LocalTime.MAX),
                        Set.of("planners"))
                .stream()
                .filter(e -> (tag + "-http").equals(e.title()) && e.virtual())
                .findFirst().orElseThrow();
        String wireRecurrenceId = objectMapper.convertValue(virtual.recurrenceId(), String.class);

        String body = "{\"seriesId\":%d,\"recurrenceId\":\"%s\"}".formatted(master.getId(), wireRecurrenceId);
        String created = mvc.perform(post("/api/calendar/occurrences/materialise")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long newId = objectMapper.readTree(created).get("id").asLong();
        assertThat(planners.findById(newId).orElseThrow().getRecurrenceId()).isEqualTo(virtual.recurrenceId());

        // ...and the same string identifies the slot again when skipping it.
        mvc.perform(post("/api/calendar/occurrences/skip")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        assertThat(planners.findById(newId)).isEmpty();   // soft-deleted, hidden by @SQLRestriction
    }

    /** End to end through the endpoint the calendar actually calls. */
    @Test
    void theCalendarReturnsVirtualOccurrencesAlongsideRealOnes() {
        Planner master = dailyHabit("-calendar", LocalTime.of(8, 0));

        List<CalendarEventDto> mine = calendar
                .events(ANCHOR.toLocalDate().atStartOfDay(), ANCHOR.plusDays(3).with(LocalTime.MAX),
                        Set.of("planners"))
                .stream()
                .filter(e -> (tag + "-calendar").equals(e.title()))
                .toList();

        assertThat(mine).hasSize(4);
        assertThat(mine).filteredOn(e -> !e.virtual()).singleElement()
                .satisfies(e -> {
                    assertThat(e.id()).isEqualTo(master.getId());     // the master row
                    assertThat(e.seriesId()).isEqualTo(master.getId());
                });
        assertThat(mine).filteredOn(CalendarEventDto::virtual).hasSize(3)
                .allSatisfy(e -> {
                    assertThat(e.id()).isNull();                      // nothing to open yet
                    assertThat(e.seriesId()).isEqualTo(master.getId());
                    assertThat(e.recurrenceId()).isEqualTo(e.start());
                });
    }
}
