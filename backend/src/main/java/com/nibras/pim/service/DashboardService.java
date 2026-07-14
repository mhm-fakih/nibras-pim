package com.nibras.pim.service;

import com.nibras.pim.domain.BaseEntity;
import com.nibras.pim.domain.Task;
import com.nibras.pim.repo.GoalRepository;
import com.nibras.pim.repo.TaskRepository;
import com.nibras.pim.web.dto.GoalDto;
import com.nibras.pim.web.dto.KanbanDto;
import com.nibras.pim.web.dto.TaskDto;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Aggregates data for the dashboards. The kanban shows bookmarked goals + tasks. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    /** Order of the unified bookmarks list (named modules first, then the rest). */
    private static final List<String> BOOKMARK_ORDER =
            List.of("departments", "courses", "planners", "journals", "goals", "tasks",
                    "resources", "notes", "writings");

    private final GoalRepository goals;
    private final TaskRepository tasks;
    private final EntityCatalog catalog;
    private final GoalService goalService;
    private final TaskService taskService;

    public DashboardService(GoalRepository goals, TaskRepository tasks, EntityCatalog catalog,
                            GoalService goalService, TaskService taskService) {
        this.goals = goals;
        this.tasks = tasks;
        this.catalog = catalog;
        this.goalService = goalService;
        this.taskService = taskService;
    }

    public record BookmarkItem(String module, Long id, String title) {
    }

    /** Every bookmarked record across all modules, grouped/ordered by module. */
    public List<BookmarkItem> bookmarks() {
        List<BookmarkItem> out = new ArrayList<>();
        for (String module : BOOKMARK_ORDER) {
            catalog.find(module).ifPresent(svc -> {
                for (BaseEntity e : svc.bookmarkedEntities()) {
                    out.add(new BookmarkItem(module, e.getId(), e.feedTitle()));
                }
            });
        }
        return out;
    }

    /** How long a completed task keeps showing (struck-out) on the kanban before dropping off. */
    private static final int KANBAN_DONE_DAYS = 7;

    public KanbanDto kanban() {
        List<GoalDto> goalDtos = goals.findAll(bookmarked()).stream()
                .map(goalService::toDto)
                .toList();
        List<TaskDto> taskDtos =
                tasks.findAll(kanbanTasks(), Sort.by(Sort.Direction.DESC, "dateCreated")).stream()
                        .map(taskService::toDto)
                        .toList();
        return new KanbanDto(goalDtos, taskDtos);
    }

    /**
     * Tasks shown on the kanban: bookmarked OR linked to a bookmarked goal, and still open
     * (no completedOn, status not "done") or completed within the last week.
     */
    private static Specification<Task> kanbanTasks() {
        LocalDateTime cutoff = LocalDate.now().minusDays(KANBAN_DONE_DAYS).atStartOfDay();
        return (root, q, cb) -> {
            var goal = root.join("goal", JoinType.LEFT);
            var status = root.join("status", JoinType.LEFT);
            Predicate visible = cb.or(
                    cb.isTrue(root.get("bookmarked")),
                    cb.isTrue(goal.get("bookmarked")));
            // Null-safe "not done": a missing status (or one without a code) counts as open.
            Predicate open = cb.and(
                    cb.isNull(root.get("completedOn")),
                    cb.or(cb.isNull(status.get("code")), cb.notEqual(status.get("code"), "done")));
            Predicate recentlyDone = cb.or(
                    cb.greaterThanOrEqualTo(root.get("completedOn"), cutoff),
                    cb.and(cb.equal(status.get("code"), "done"),
                            cb.greaterThanOrEqualTo(root.get("lastUpdated"), cutoff)));
            return cb.and(visible, cb.or(open, recentlyDone));
        };
    }

    private static <T> Specification<T> bookmarked() {
        return (root, q, cb) -> cb.isTrue(root.get("bookmarked"));
    }

    // ---- KPIs (Indicators page) -------------------------------------------------------

    /** One headline number for the Indicators dashboard. */
    public record Kpi(String key, String label, long value, String hint) {
    }

    /** Standard at-a-glance counts: today's activity across all modules + task health. */
    public List<Kpi> kpis() {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = today.atTime(LocalTime.MAX);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekAgo = now.minusDays(7);

        long addedToday = 0;
        long addedWeek = 0;
        long updatedToday = 0;
        long bookmarked = 0;
        for (AbstractCrudService<?, ?> svc : catalog.all()) {
            addedToday += svc.countCreatedBetween(dayStart, dayEnd);
            addedWeek += svc.countCreatedBetween(weekAgo, now);
            updatedToday += svc.countUpdatedBetween(dayStart, dayEnd);
            bookmarked += svc.countBookmarked();
        }

        long completedToday = tasks.count((root, q, cb) ->
                cb.between(root.get("completedOn"), dayStart, dayEnd));
        long tasksDueToday = tasks.count((root, q, cb) ->
                cb.between(root.get("endDate"), dayStart, dayEnd));
        long overdueOpen = tasks.count((root, q, cb) -> cb.and(
                cb.lessThan(root.get("endDate"), now),
                cb.isNull(root.get("completedOn"))));

        List<Kpi> out = new ArrayList<>();
        out.add(new Kpi("addedToday", "Records added today", addedToday, "all modules"));
        out.add(new Kpi("completedToday", "Tasks completed today", completedToday, "by completed date"));
        out.add(new Kpi("updatedToday", "Records updated today", updatedToday, "all modules"));
        out.add(new Kpi("dueToday", "Tasks due today", tasksDueToday, "by end date"));
        out.add(new Kpi("overdueOpen", "Tasks overdue", overdueOpen, "past due, not completed"));
        out.add(new Kpi("addedWeek", "Records added (7 days)", addedWeek, "all modules"));
        out.add(new Kpi("bookmarked", "Bookmarked records", bookmarked, "all modules"));
        return out;
    }
}
