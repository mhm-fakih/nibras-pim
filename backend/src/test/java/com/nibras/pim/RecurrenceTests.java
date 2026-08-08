package com.nibras.pim;

import com.nibras.pim.domain.Task;
import com.nibras.pim.repo.TaskRepository;
import com.nibras.pim.service.TaskRecurrenceService;
import com.nibras.pim.support.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression cover for "a task set to repeat monthly repeats every day".
 *
 * The fixed-schedule sweep runs nightly and spawns every recurring task whose due date has passed.
 * It used to advance the new occurrence a single step from the old anchor, so an overdue monthly
 * task landed on a date that was still in the past — immediately due again, spawning another copy
 * the next night, one per day until the chain crawled back to the present. The interval the user
 * configured was irrelevant to how often tasks actually appeared.
 */
class RecurrenceTests extends AuthTestSupport {

    /**
     * Unique per run. The sweep is global and this database is shared and persistent, so a fixed
     * schedule task left behind by an earlier run would still be due and would be swept up here,
     * making the assertions depend on history rather than on this test's own chain.
     */
    private final String tag = "recur-" + System.nanoTime();

    @Autowired
    TaskRecurrenceService recurrence;

    @Autowired
    TaskRepository tasks;

    /** Scoped to one chain: the sweep is global and this database is shared with other tests. */
    private long countBySummary(String summary) {
        return tasks.findAll().stream().filter(t -> summary.equals(t.getSummary())).count();
    }

    /**
     * A monthly fixed-schedule task whose due date is a whole number of months in the past.
     *
     * The anchor is truncated to the second and pushed a minute further back on purpose. {@code
     * end_date} is a second-resolution MySQL {@code datetime} and MySQL <em>rounds</em> fractional
     * seconds rather than truncating them, so a plain {@code now().minusMonths(n)} can be stored up
     * to half a second LATER than it was computed. Catching that anchor back up by n months then
     * lands a hair in the future, the assertion "the new occurrence is in the future" passes for a
     * useless reason, and the next sweep — microseconds later — sees it as due and spawns again.
     * That made this test fail about one run in three, and only inside the full suite, where the
     * timing lines up.
     */
    private Task monthlyFixedTaskDueMonthsAgo(String summary, int monthsOverdue) {
        Task t = new Task();
        t.setSummary(summary);
        t.setRecurring(true);
        t.setFixedSchedule(true);
        t.setRecurrencePreset("MONTHLY");
        t.setRecurrenceInterval(1);
        t.setEndDate(LocalDateTime.now().withNano(0).minusMinutes(1).minusMonths(monthsOverdue));
        return tasks.saveAndFlush(t);
    }

    @Test
    void monthlyTaskOverdueByMonthsCatchesUpInOneSweepInsteadOfOnePerDay() {
        Task source = monthlyFixedTaskDueMonthsAgo(tag + "-overdue", 6);

        Task next = recurrence.spawnNext(source);

        assertThat(next).isNotNull();
        // The whole bug in one assertion: the new occurrence must be in the FUTURE. If it lands in
        // the past it is due immediately and tomorrow's sweep spawns yet another.
        assertThat(next.getEndDate()).isAfter(LocalDateTime.now());
        // ...and it must still sit on the monthly grid, not merely "now + something".
        assertThat(next.getEndDate()).isBefore(LocalDateTime.now().plusMonths(1).plusDays(1));
    }

    @Test
    void sweepingRepeatedlyDoesNotKeepSpawningTheSameMonthlyTask() {
        monthlyFixedTaskDueMonthsAgo(tag + "-sweep", 3);

        int first = recurrence.materializeDueFixedScheduleTasks();
        assertThat(first).isPositive();

        // Simulates the next night's sweep. The occurrence just created is in the future, so it is
        // not due — nothing more should be produced for this chain.
        long before = countBySummary(tag + "-sweep");
        recurrence.materializeDueFixedScheduleTasks();
        assertThat(countBySummary(tag + "-sweep")).isEqualTo(before);
    }

    /**
     * The legacy schema stores the preset in a varchar column that really does hold nulls and stray
     * values. Such a rule can't advance a date, and spawning "one step later" produced an exact
     * copy on the same dates — due again immediately, every night, forever.
     */
    @Test
    void unusableRecurrenceRuleSpawnsNothingRatherThanACopyPerDay() {
        Task t = new Task();
        t.setSummary(tag + "-broken");
        t.setRecurring(true);
        t.setFixedSchedule(true);
        t.setRecurrencePreset(null);
        t.setEndDate(LocalDateTime.now().minusDays(2));
        tasks.saveAndFlush(t);

        long before = countBySummary(tag + "-broken");
        recurrence.materializeDueFixedScheduleTasks();
        assertThat(countBySummary(tag + "-broken")).isEqualTo(before);
    }

    @Test
    void computeNextStillAdvancesExactlyOneStepPerPreset() {
        LocalDateTime base = LocalDateTime.of(2026, 1, 15, 9, 0);
        assertThat(recurrence.computeNext(base, "DAILY", 1, null)).isEqualTo(base.plusDays(1));
        assertThat(recurrence.computeNext(base, "WEEKLY", 2, null)).isEqualTo(base.plusWeeks(2));
        assertThat(recurrence.computeNext(base, "MONTHLY", 1, null)).isEqualTo(base.plusMonths(1));
        assertThat(recurrence.computeNext(base, "YEARLY", 1, null)).isEqualTo(base.plusYears(1));
    }
}
