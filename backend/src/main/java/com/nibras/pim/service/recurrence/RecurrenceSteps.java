package com.nibras.pim.service.recurrence;

import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;

/**
 * The single definition of what DAILY / WEEKLY / MONTHLY / YEARLY / CUSTOM mean.
 *
 * Both recurrence models in the app go through here: task recurrence, which materialises one
 * occurrence at a time ({@link com.nibras.pim.service.TaskRecurrenceService}), and planner
 * recurrence, which expands a rule into virtual occurrences at read time
 * ({@link com.nibras.pim.service.PlannerSeriesService}). They differ in what they do with the
 * dates, not in what the dates are — keeping the step function in one place is what stops the two
 * from quietly disagreeing about, say, what "monthly from the 31st" produces in February.
 */
public final class RecurrenceSteps {

    private RecurrenceSteps() {
    }

    /**
     * Advances {@code from} by one recurrence step. Returns {@code from} unchanged when the rule
     * can't move it — a null/unrecognised preset, or a blank CUSTOM cron. Callers must treat "did
     * not move" as an unusable rule rather than looping on it.
     *
     * <p>Month arithmetic clamps: monthly from the 31st gives Feb 28/29, then Mar 31 again, because
     * {@link LocalDateTime#plusMonths} keeps the day-of-month where it can. That is the behaviour
     * every calendar app has, and it is why the step is always taken from the ORIGINAL anchor
     * rather than from the previous clamped result.
     */
    public static LocalDateTime next(LocalDateTime from, String preset, Integer interval, String cron) {
        LocalDateTime base = from == null ? LocalDateTime.now() : from;
        int n = interval == null || interval < 1 ? 1 : interval;
        if (preset == null) {
            return base;
        }
        return switch (preset.toUpperCase()) {
            case "DAILY" -> base.plusDays(n);
            case "WEEKLY" -> base.plusWeeks(n);
            case "MONTHLY" -> base.plusMonths(n);
            case "YEARLY" -> base.plusYears(n);
            case "CUSTOM" -> cron == null || cron.isBlank() ? base : CronExpression.parse(cron).next(base);
            default -> base;
        };
    }

    /**
     * The {@code n}-th occurrence after the anchor, computed from the anchor rather than by
     * repeated stepping, so a clamped month can't drag the whole series backwards (monthly from
     * Jan 31 must give Mar 31, not Feb 28 → Mar 28). Falls back to iteration for CUSTOM, whose
     * cron has no closed form.
     *
     * @return null when the rule can't advance at all, or when the {@code n}-th occurrence falls
     *         outside the range of a {@link LocalDateTime}. Callers treat both as "no more
     *         occurrences", which is the truthful answer in either case — a caller that let the
     *         DateTimeException escape would 500 the whole calendar over one bad rule.
     */
    public static LocalDateTime nth(LocalDateTime anchor, int n, String preset, Integer interval, String cron) {
        if (n <= 0) {
            return anchor;
        }
        int step = interval == null || interval < 1 ? 1 : interval;
        if (preset == null) {
            return null;
        }
        try {
            return switch (preset.toUpperCase()) {
                case "DAILY" -> anchor.plusDays((long) step * n);
                case "WEEKLY" -> anchor.plusWeeks((long) step * n);
                case "MONTHLY" -> anchor.plusMonths((long) step * n);
                case "YEARLY" -> anchor.plusYears((long) step * n);
                case "CUSTOM" -> {
                    LocalDateTime at = anchor;
                    for (int i = 0; i < n; i++) {
                        LocalDateTime moved = next(at, preset, interval, cron);
                        if (moved == null || !moved.isAfter(at)) {
                            yield null;
                        }
                        at = moved;
                    }
                    yield at;
                }
                default -> null;
            };
        } catch (java.time.DateTimeException e) {
            return null;   // ran off the end of the calendar
        }
    }

    /** True when the rule actually moves a date forward — i.e. it is usable at all. */
    public static boolean usable(LocalDateTime anchor, String preset, Integer interval, String cron) {
        if (anchor == null) {
            return false;
        }
        LocalDateTime moved = next(anchor, preset, interval, cron);
        return moved != null && moved.isAfter(anchor);
    }
}
