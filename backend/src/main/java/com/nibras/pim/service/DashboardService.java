package com.nibras.pim.service;

import com.nibras.pim.domain.BaseEntity;
import com.nibras.pim.domain.Goal;
import com.nibras.pim.domain.Task;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.domain.lookup.GoalType;
import com.nibras.pim.domain.lookup.WorkStatus;
import com.nibras.pim.repo.GoalRepository;
import com.nibras.pim.repo.TaskRepository;
import com.nibras.pim.web.dto.KanbanCardDto;
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

    public DashboardService(GoalRepository goals, TaskRepository tasks, EntityCatalog catalog) {
        this.goals = goals;
        this.tasks = tasks;
        this.catalog = catalog;
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

    public List<KanbanCardDto> kanban() {
        List<KanbanCardDto> cards = new ArrayList<>();

        for (Goal g : goals.findAll(bookmarked())) {
            Department d = g.getDepartment();
            GoalType t = g.getType();
            cards.add(new KanbanCardDto("goals", g.getId(), g.feedTitle(), g.getPriority(),
                    d == null ? null : d.getId(), d == null ? null : d.getCode(),
                    d == null ? null : d.getName(),
                    t == null ? null : t.getId(), t == null ? null : t.getName()));
        }
        for (Task k : tasks.findAll(bookmarked())) {
            Department d = k.getDepartment();
            WorkStatus s = k.getStatus();
            cards.add(new KanbanCardDto("tasks", k.getId(), k.feedTitle(), k.getPriority(),
                    d == null ? null : d.getId(), d == null ? null : d.getCode(),
                    d == null ? null : d.getName(),
                    s == null ? null : s.getId(), s == null ? null : s.getName()));
        }
        return cards;
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
