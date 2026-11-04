package com.nibras.pim.service;

import com.nibras.pim.web.dto.CalendarEventDto;
import com.nibras.pim.web.dto.CustomDashboardDto;
import com.nibras.pim.web.dto.CustomDashboardRegionDto;
import com.nibras.pim.web.dto.DashboardFileContentDto;
import com.nibras.pim.web.dto.FilterParams;
import com.nibras.pim.web.dto.TaskDto;
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
import java.util.Map;
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
    private final com.nibras.pim.service.checklists.ChecklistService checklists;
    private final TaskService tasks;
    private final com.nibras.pim.repo.TaskRepository taskRepo;
    private final com.nibras.pim.service.localnotes.LocalNotesService localNotes;
    private final SettingService settings;
    private final com.nibras.pim.repo.PlannerRepository plannerRepo;
    private final com.nibras.pim.service.PlannerService planners;
    private final RepositoryService repos;

    public PublicDashboardService(CustomDashboardService dashboards, IndexCardService notes, GoalService goals,
                                  IndicatorService indicators, IndicatorMonitorService indicatorMonitor,
                                  CalendarEventService calendarEvents, DashboardService kanban,
                                  com.nibras.pim.service.checklists.ChecklistService checklists, TaskService tasks,
                                  com.nibras.pim.repo.TaskRepository taskRepo,
                                  com.nibras.pim.service.localnotes.LocalNotesService localNotes,
                                  SettingService settings,
                                  com.nibras.pim.repo.PlannerRepository plannerRepo,
                                  com.nibras.pim.service.PlannerService planners,
                                  RepositoryService repos) {
        this.repos = repos;
        this.plannerRepo = plannerRepo;
        this.planners = planners;
        this.dashboards = dashboards;
        this.settings = settings;
        this.notes = notes;
        this.goals = goals;
        this.indicators = indicators;
        this.indicatorMonitor = indicatorMonitor;
        this.calendarEvents = calendarEvents;
        this.kanban = kanban;
        this.checklists = checklists;
        this.tasks = tasks;
        this.taskRepo = taskRepo;
        this.localNotes = localNotes;
    }

    public record PublicRegion(
            Long id, Integer orderIndex, String componentType, Integer colSpan, Integer rowSpan, String config,
            IndexCardDto quote,
            List<CalendarEventDto> calendarEvents,
            List<GoalHeatmap> goalsHeatmap,
            KanbanDto kanbanBoard,
            DashboardFileContentDto file,
            com.nibras.pim.service.checklists.ChecklistWeekFile.Week checklist,
            List<TaskDto> tasks,
            List<com.nibras.pim.web.dto.LocalNoteDto> localTasks,
            /** 'checklist' regions: the week before this one, for a multi-day view reaching into it. */
            com.nibras.pim.service.checklists.ChecklistWeekFile.Week checklistPrevious,
            /** 'plans' regions: bookmarked planner entries of the chosen types. */
            List<com.nibras.pim.web.dto.PlannerDto> plans,
            /** 'tasks' regions: how many files each task has in repository 1, by task id. */
            Map<Long, Integer> taskFiles) {
    }

    public record GoalHeatmap(Long goalId, String goalCode, List<IndicatorWithSeries> indicators) {
    }

    public record IndicatorWithSeries(IndicatorDto indicator, List<IndicatorMonitorService.SeriesPoint> series) {
    }

    public record Snapshot(Long id, String name, Integer columnsCount, Integer refreshMinutes,
                           String columnWidths, String rowHeights, Boolean hour12, List<PublicRegion> regions) {
    }

    public Snapshot snapshot(Long id) {
        CustomDashboardDto d = dashboards.get(id);
        List<PublicRegion> regions = d.regions().stream().map(this::resolve).toList();
        // The calendars' clock rides along: the public view can't read settings itself.
        boolean hour12 = !"false".equals(settings.get("calendar.hour12", "true"));
        return new Snapshot(d.id(), d.name(), d.columnsCount(), d.refreshMinutes(), d.columnWidths(), d.rowHeights(), hour12, regions);
    }

    private PublicRegion resolve(CustomDashboardRegionDto r) {
        return switch (r.componentType()) {
            case "quote" -> withQuote(r, resolveQuote(r.config()));
            case "calendar" -> withCalendar(r, resolveCalendar(r.config()));
            case "goalsHeatmap" -> withGoalsHeatmap(r);
            case "kanban" -> withKanban(r);
            case "file" -> withFile(r, resolveFile(r.config()));
            case "checklist" -> new PublicRegion(r.id(), r.orderIndex(), r.componentType(), r.colSpan(), r.rowSpan(), r.config(),
                    null, null, null, null, null, resolveChecklist(0), null, null, resolveChecklist(1), null, null);
            case "localTasks" -> new PublicRegion(r.id(), r.orderIndex(), r.componentType(), r.colSpan(), r.rowSpan(), r.config(),
                    null, null, null, null, null, null, null, resolveLocalTasks(r.config()), null, null, null);
            case "plans" -> new PublicRegion(r.id(), r.orderIndex(), r.componentType(), r.colSpan(), r.rowSpan(), r.config(),
                    null, null, null, null, null, null, null, null, null, resolvePlans(r.config()), null);
            case "tasks" -> withTasks(r);
            default -> withQuote(r, null);
        };
    }

    private PublicRegion withQuote(CustomDashboardRegionDto r, IndexCardDto note) {
        return new PublicRegion(r.id(), r.orderIndex(), r.componentType(), r.colSpan(), r.rowSpan(), r.config(), note, null, null, null, null, null, null, null, null, null, null);
    }

    private PublicRegion withCalendar(CustomDashboardRegionDto r, List<CalendarEventDto> events) {
        return new PublicRegion(r.id(), r.orderIndex(), r.componentType(), r.colSpan(), r.rowSpan(), r.config(), null, events, null, null, null, null, null, null, null, null, null);
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
        return new PublicRegion(r.id(), r.orderIndex(), r.componentType(), r.colSpan(), r.rowSpan(), r.config(), null, null, groups, null, null, null, null, null, null, null, null);
    }

    private PublicRegion withKanban(CustomDashboardRegionDto r) {
        return new PublicRegion(r.id(), r.orderIndex(), r.componentType(), r.colSpan(), r.rowSpan(), r.config(), null, null, null, kanban.kanban(), null, null, null, null, null, null, null);
    }

    private PublicRegion withFile(CustomDashboardRegionDto r, DashboardFileContentDto file) {
        return new PublicRegion(r.id(), r.orderIndex(), r.componentType(), r.colSpan(), r.rowSpan(), r.config(), null, null, null, null, file, null, null, null, null, null, null);
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
                Set.of("journals", "planners", "localnotes"), eventType == null || eventType.isBlank() ? null : eventType);
    }

    /**
     * The checklist {@code weeksBack} weeks ago — 0 this week, 1 last week (a multi-day view
     * starting early in the week reaches into it). A week with no file yet shows the last one's
     * items unmarked; null when there is none.
     */
    private com.nibras.pim.service.checklists.ChecklistWeekFile.Week resolveChecklist(int weeksBack) {
        try {
            return checklists.week(com.nibras.pim.service.checklists.ChecklistWeekFile.idOf(
                    LocalDate.now().minusWeeks(weeksBack)));
        } catch (org.springframework.web.server.ResponseStatusException none) {
            return null;
        }
    }

    /**
     * Open tasks (status marked active, or no status), the ones with a due date first, soonest
     * first, then the undated ones by priority, highest first, most recently touched first. Config:
     * {@code limit} caps the list (default 30); {@code overdueDays} leaves out tasks overdue by more
     * than that many days (default 30, 0 keeps them all), so years-old leftovers don't crowd the
     * top; {@code bookmarkedOnly} keeps only bookmarked tasks.
     */
    /**
     * Bookmarked planner entries of the configured types — {@code types}: comma-separated codes or
     * names ("focus,policy"; blank = every type) — newest first, at most {@code limit} (default 20).
     */
    private List<com.nibras.pim.web.dto.PlannerDto> resolvePlans(String config) {
        String raw = jsonString(config, "types");
        List<String> types = raw == null ? List.of() : java.util.Arrays.stream(raw.split(","))
                .map(String::trim).filter(x -> !x.isEmpty()).map(String::toLowerCase).toList();
        int limit = Math.max(1, Math.min(100, jsonInt(config, "limit", 20)));
        return plannerRepo.bookmarkedPlans(types.isEmpty(), types.isEmpty() ? List.of("") : types, PageRequest.of(0, limit))
                .stream().map(planners::get).toList();
    }

    private PublicRegion withTasks(CustomDashboardRegionDto r) {
        List<TaskDto> list = resolveTasks(r.config());
        return new PublicRegion(r.id(), r.orderIndex(), r.componentType(), r.colSpan(), r.rowSpan(), r.config(),
                null, null, null, null, null, null, list, null, null, null, taskFiles(list));
    }

    /** Files in each task's repository-1 folder (its working files), by task id. */
    private Map<Long, Integer> taskFiles(List<TaskDto> list) {
        Map<Long, Integer> out = new java.util.LinkedHashMap<>();
        for (TaskDto t : list) {
            // Tasks aren't filed by type in the repositories (only resources are), so no type level.
            out.put(t.id(), repos.countFiles(1, "tasks", t.id(), null));
        }
        return out;
    }

    private List<TaskDto> resolveTasks(String config) {
        int limit = Math.max(1, Math.min(200, jsonInt(config, "limit", 30)));
        int overdueDays = Math.max(0, jsonInt(config, "overdueDays", 30));
        boolean bookmarkedOnly = jsonBool(config, "bookmarkedOnly");
        java.time.LocalDateTime cutoff = overdueDays == 0 ? null
                : LocalDate.now().minusDays(overdueDays).atStartOfDay();
        return taskRepo.openForDashboard(cutoff, bookmarkedOnly, PageRequest.of(0, limit)).stream()
                .map(tasks::get)
                .toList();
    }

    /**
     * Tasks written as notes in the local notes folder (type T in the file name) at priority
     * {@code minPriority} or higher (default 3): highest priority first, newest first. {@code limit}
     * caps the list (default 30). Empty when the folder isn't set up.
     */
    private List<com.nibras.pim.web.dto.LocalNoteDto> resolveLocalTasks(String config) {
        int minPriority = Math.max(1, jsonInt(config, "minPriority", 3));
        int limit = Math.max(1, Math.min(200, jsonInt(config, "limit", 30)));
        try {
            return localNotes.view().days().stream()
                    .flatMap(d -> d.notes().stream())
                    .filter(n -> "T".equalsIgnoreCase(n.type()) && n.priority() != null && n.priority() >= minPriority)
                    .sorted(java.util.Comparator.comparing(com.nibras.pim.web.dto.LocalNoteDto::priority).reversed()
                            .thenComparing(com.nibras.pim.web.dto.LocalNoteDto::timestamp, java.util.Comparator.reverseOrder()))
                    .limit(limit)
                    .toList();
        } catch (RuntimeException unavailable) {
            return List.of();
        }
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
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static FilterParams filterByTypeBookmarked(Long typeId) {
        return new FilterParams(null, null, null, null, null, Boolean.TRUE, null, null,
                null, typeId, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static FilterParams filterByGoal(Long goalId) {
        return new FilterParams(null, null, null, null, null, null, null, null,
                null, null, null, null, goalId, null, null, null, null, null, null, null, null, null, null, null, null, null);
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

    private static boolean jsonBool(String json, String key) {
        return json != null && java.util.regex.Pattern
                .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*true")
                .matcher(json).find();
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
