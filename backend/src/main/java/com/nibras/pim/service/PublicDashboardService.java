package com.nibras.pim.service;

import com.nibras.pim.web.dto.CalendarEventDto;
import com.nibras.pim.web.dto.CustomDashboardDto;
import com.nibras.pim.web.dto.CustomDashboardRegionDto;
import com.nibras.pim.web.dto.DashboardFileContentDto;
import com.nibras.pim.web.dto.FilterParams;
import com.nibras.pim.web.dto.GoalDto;
import com.nibras.pim.web.dto.IndexCardDto;
import com.nibras.pim.web.dto.IndicatorDto;
import com.nibras.pim.web.dto.KanbanDto;
import com.nibras.pim.web.dto.LookupDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;

/**
 * Fully-resolved, read-only snapshot of one {@link com.nibras.pim.domain.CustomDashboard} — every
 * region's data pre-fetched server-side into one payload. Backs the chrome-free "view" page meant
 * to be left open on a TV or spare monitor with no login: rather than opening the generic
 * notes/goals/indicators/calendar/kanban read endpoints to anonymous callers (which would let
 * anyone query any record via those endpoints' own filters), this exposes only the exact data each
 * region of a specific, already-configured dashboard needs — nothing else.
 */
@Service
@Transactional(readOnly = true)
public class PublicDashboardService {

    private final CustomDashboardService dashboards;
    private final IndexCardService notes;
    private final GoalService goals;
    private final IndicatorService indicators;
    private final IndicatorMonitorService indicatorMonitor;
    private final CalendarEventService calendarEvents;
    private final DashboardService kanban;

    public PublicDashboardService(CustomDashboardService dashboards, IndexCardService notes, GoalService goals,
                                  IndicatorService indicators, IndicatorMonitorService indicatorMonitor,
                                  CalendarEventService calendarEvents, DashboardService kanban) {
        this.dashboards = dashboards;
        this.notes = notes;
        this.goals = goals;
        this.indicators = indicators;
        this.indicatorMonitor = indicatorMonitor;
        this.calendarEvents = calendarEvents;
        this.kanban = kanban;
    }

    public record PublicRegion(
            Long id, Integer orderIndex, String componentType, Integer colSpan, String config,
            IndexCardDto quote,
            List<CalendarEventDto> calendarEvents,
            List<GoalHeatmap> goalsHeatmap,
            KanbanDto kanbanBoard,
            DashboardFileContentDto file) {
    }

    public record GoalHeatmap(Long goalId, String goalCode, List<IndicatorWithSeries> indicators) {
    }

    public record IndicatorWithSeries(IndicatorDto indicator, List<IndicatorMonitorService.SeriesPoint> series) {
    }

    public record Snapshot(Long id, String name, Integer columnsCount, Integer refreshMinutes,
                           List<PublicRegion> regions) {
    }

    public Snapshot snapshot(Long id) {
        CustomDashboardDto d = dashboards.get(id);
        List<PublicRegion> regions = d.regions().stream().map(this::resolve).toList();
        return new Snapshot(d.id(), d.name(), d.columnsCount(), d.refreshMinutes(), regions);
    }

    private PublicRegion resolve(CustomDashboardRegionDto r) {
        return switch (r.componentType()) {
            case "quote" -> withQuote(r, resolveQuote(r.config()));
            case "calendar" -> withCalendar(r, resolveCalendar(r.config()));
            case "goalsHeatmap" -> withGoalsHeatmap(r);
            case "kanban" -> withKanban(r);
            case "file" -> withFile(r, resolveFile(r.config()));
            default -> withQuote(r, null);
        };
    }

    private PublicRegion withQuote(CustomDashboardRegionDto r, IndexCardDto note) {
        return new PublicRegion(r.id(), r.orderIndex(), r.componentType(), r.colSpan(), r.config(), note, null, null, null, null);
    }

    private PublicRegion withCalendar(CustomDashboardRegionDto r, List<CalendarEventDto> events) {
        return new PublicRegion(r.id(), r.orderIndex(), r.componentType(), r.colSpan(), r.config(), null, events, null, null, null);
    }

    private PublicRegion withGoalsHeatmap(CustomDashboardRegionDto r) {
        List<GoalDto> dashboardGoals = goals.list(
                filterByDashboardFlag(),
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "dateCreated"))).getContent();
        List<GoalHeatmap> groups = dashboardGoals.stream()
                .map(g -> {
                    List<IndicatorDto> ind = indicators.list(
                            filterByGoal(g.id()),
                            PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "dateCreated"))).getContent();
                    List<IndicatorWithSeries> withSeries = ind.stream()
                            .map(i -> new IndicatorWithSeries(i, indicatorMonitor.series(i.id())))
                            .toList();
                    return new GoalHeatmap(g.id(), g.code(), withSeries);
                })
                .filter(g -> !g.indicators().isEmpty())
                .toList();
        return new PublicRegion(r.id(), r.orderIndex(), r.componentType(), r.colSpan(), r.config(), null, null, groups, null, null);
    }

    private PublicRegion withKanban(CustomDashboardRegionDto r) {
        return new PublicRegion(r.id(), r.orderIndex(), r.componentType(), r.colSpan(), r.config(), null, null, null, kanban.kanban(), null);
    }

    private PublicRegion withFile(CustomDashboardRegionDto r, DashboardFileContentDto file) {
        return new PublicRegion(r.id(), r.orderIndex(), r.componentType(), r.colSpan(), r.config(), null, null, null, null, file);
    }

    private IndexCardDto resolveQuote(String config) {
        String typeCode = jsonString(config, "typeCode");
        if (typeCode == null) {
            return null;
        }
        Long typeId = notes.lookups().getOrDefault("types", List.of()).stream()
                .filter(t -> typeCode.equals(t.code()))
                .map(LookupDto::id)
                .findFirst().orElse(null);
        if (typeId == null) {
            return null;
        }
        List<IndexCardDto> found = notes.list(
                filterByTypeBookmarked(typeId),
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "lastUpdated"))).getContent();
        return found.isEmpty() ? null : found.get(0);
    }

    private List<CalendarEventDto> resolveCalendar(String config) {
        String eventType = jsonString(config, "eventType");
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        return calendarEvents.events(monday.atStartOfDay(), sunday.atTime(LocalTime.MAX),
                Set.of("journals", "planners"), eventType == null || eventType.isBlank() ? null : eventType);
    }

    private DashboardFileContentDto resolveFile(String config) {
        int repo = jsonInt(config, "repo", 1);
        String path = jsonString(config, "path");
        if (path == null || path.isBlank()) {
            return null;
        }
        return dashboards.readFile(repo, path);
    }

    // ---- FilterParams builders (only the field each caller needs is non-null) --------------

    private static FilterParams filterByDashboardFlag() {
        return new FilterParams(null, null, null, null, null, null, null, Boolean.TRUE,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static FilterParams filterByTypeBookmarked(Long typeId) {
        return new FilterParams(null, null, null, null, null, Boolean.TRUE, null, null,
                null, typeId, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static FilterParams filterByGoal(Long goalId) {
        return new FilterParams(null, null, null, null, null, null, null, null,
                null, null, null, goalId, null, null, null, null, null, null, null, null, null, null, null);
    }

    // ---- tiny hand-rolled JSON field readers (config is a flat {"key":"value"} blob) -------

    private static String jsonString(String json, String key) {
        if (json == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static int jsonInt(String json, String key, int fallback) {
        if (json == null) {
            return fallback;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)")
                .matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : fallback;
    }
}
