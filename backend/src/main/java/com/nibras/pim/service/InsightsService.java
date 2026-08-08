package com.nibras.pim.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Aggregates per-module creation/update activity for the Insights page — a fixed, built-in
 *  analytics view (as opposed to the user-configurable custom-dashboard builder). */
@Service
@Transactional(readOnly = true)
public class InsightsService {

    /** The "main" record types this page reports on — the content types users actually create and
     *  revisit, as opposed to lookup/reference modules like departments/courses/tags. */
    private static final List<String> CORE_MODULES =
            List.of("tasks", "journals", "planners", "resources", "notes", "writings", "goals");

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("tasks", "Tasks"), Map.entry("journals", "Journal"),
            Map.entry("planners", "Planner"), Map.entry("resources", "Resources"),
            Map.entry("notes", "Notes"), Map.entry("writings", "Writings"),
            Map.entry("goals", "Goals"));

    private final EntityCatalog catalog;

    public InsightsService(EntityCatalog catalog) {
        this.catalog = catalog;
    }

    public record DayCount(LocalDate date, long count) {
    }

    public record ModuleInsight(
            String key, String label, long total,
            long createdRecent, long updatedRecent, double percentUpdatedRecent,
            List<DayCount> createdSeries, List<DayCount> updatedSeries) {
    }

    public record InsightsSummary(LocalDate from, LocalDate to, int reviewDays, List<ModuleInsight> modules) {
    }

    /**
     * @param from       start of the covered period — kept short (e.g. last 3 months) by the caller
     *                   so the page motivates reviewing recent work, not a multi-year backlog.
     * @param to         end of the covered period.
     * @param reviewDays the trailing "review window" (e.g. last week) used to decide whether a
     *                   period record counts as reviewed.
     */
    public InsightsSummary summary(LocalDate from, LocalDate to, int reviewDays) {
        LocalDateTime rangeStart = from.atStartOfDay();
        LocalDateTime rangeEnd = to.atTime(LocalTime.MAX);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reviewCutoff = now.minusDays(reviewDays);

        // LinkedHashMap: preserves CORE_MODULES order in the response without a second pass.
        Map<String, ModuleInsight> byKey = new LinkedHashMap<>();
        for (String key : CORE_MODULES) {
            catalog.find(key).ifPresent(svc -> {
                // Scoped to the period (not svc.countAll()) so the denominator — and the pie/ranking
                // built on it — reflects recent work, not the entire all-time record count.
                long total = svc.countCreatedBetween(rangeStart, rangeEnd);
                long createdRecent = svc.countCreatedBetween(reviewCutoff, now);
                long updatedRecent = svc.countUpdatedBetween(reviewCutoff, now);
                double percent = total == 0 ? 0.0 : (updatedRecent * 100.0 / total);
                List<DayCount> created = toSeries(svc.creationCounts(rangeStart, rangeEnd));
                List<DayCount> updated = toSeries(svc.updateCounts(rangeStart, rangeEnd));
                byKey.put(key, new ModuleInsight(key, LABELS.getOrDefault(key, key), total,
                        createdRecent, updatedRecent, percent, created, updated));
            });
        }
        return new InsightsSummary(from, to, reviewDays, List.copyOf(byKey.values()));
    }

    /** Same [java.sql.Date, Long] row shape as CalendarController's day-count merge. */
    private static List<DayCount> toSeries(List<Object[]> rows) {
        TreeMap<LocalDate, Long> byDay = new TreeMap<>();
        for (Object[] row : rows) {
            LocalDate day = ((java.sql.Date) row[0]).toLocalDate();
            long count = ((Number) row[1]).longValue();
            byDay.merge(day, count, Long::sum);
        }
        List<DayCount> out = new ArrayList<>();
        byDay.forEach((date, count) -> out.add(new DayCount(date, count)));
        return out;
    }
}
