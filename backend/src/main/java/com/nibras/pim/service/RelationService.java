package com.nibras.pim.service;

import com.nibras.pim.domain.BaseEntity;
import com.nibras.pim.domain.Book;
import com.nibras.pim.domain.Excerpt;
import com.nibras.pim.domain.Goal;
import com.nibras.pim.domain.IndexCard;
import com.nibras.pim.domain.Journal;
import com.nibras.pim.domain.Planner;
import com.nibras.pim.domain.Task;
import com.nibras.pim.domain.Writing;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Records related to a given record through the legacy foreign keys. Most main tables carry a
 * single {@code course} and {@code department}, plus targeted cross-links (a Book's excerpts;
 * a Goal's tasks/journals/planners; a Writing's notes; a Book read in journals/planners). Driven by
 * a registry of {@code parent → child (via FK)} relations, queried generically by JPQL.
 */
@Service
public class RelationService {

    /** parent slug, child slug, child entity, the child's FK property to the parent, display label. */
    private record Rel(String parent, String child, Class<? extends BaseEntity> childClass,
                       String fk, String label) {
    }

    private static final int ITEM_LIMIT = 30;

    private static final List<Rel> RELATIONS = List.of(
            // A course / department owns one of (almost) every main record.
            new Rel("courses", "tasks", Task.class, "course", "Tasks"),
            new Rel("courses", "goals", Goal.class, "course", "Goals"),
            new Rel("courses", "journals", Journal.class, "course", "Journal entries"),
            new Rel("courses", "planners", Planner.class, "course", "Planner entries"),
            new Rel("courses", "resources", Book.class, "course", "Resources"),
            new Rel("courses", "notes", IndexCard.class, "course", "Notes"),
            new Rel("courses", "writings", Writing.class, "course", "Writings"),
            new Rel("departments", "tasks", Task.class, "department", "Tasks"),
            new Rel("departments", "goals", Goal.class, "department", "Goals"),
            new Rel("departments", "journals", Journal.class, "department", "Journal entries"),
            new Rel("departments", "planners", Planner.class, "department", "Planner entries"),
            new Rel("departments", "resources", Book.class, "department", "Resources"),
            new Rel("departments", "notes", IndexCard.class, "department", "Notes"),
            new Rel("departments", "writings", Writing.class, "department", "Writings"),
            // Goal progress: its tasks + journal/planner entries about it.
            new Rel("goals", "tasks", Task.class, "goal", "Tasks"),
            new Rel("goals", "journals", Journal.class, "goal", "Journal entries"),
            new Rel("goals", "planners", Planner.class, "goal", "Planner entries"),
            // A task: the journal entries (e.g. time-tracking sessions) logged against it.
            new Rel("tasks", "journals", Journal.class, "task", "Journal entries"),
            // A resource (book): its excerpts + journal/planner entries that read/plan it.
            new Rel("resources", "excerpts", Excerpt.class, "book", "Excerpts"),
            new Rel("resources", "journals", Journal.class, "book", "Journal entries"),
            new Rel("resources", "planners", Planner.class, "book", "Planner entries"),
            // A writing: its notes.
            new Rel("writings", "notes", IndexCard.class, "writing", "Notes"));

    @PersistenceContext
    private EntityManager em;

    public record RelatedItem(String module, Long id, String title) {
    }

    public record RelatedGroup(String module, String label, String fk, int count, List<RelatedItem> items) {
    }

    @Transactional(readOnly = true)
    public List<RelatedGroup> related(String module, Long id) {
        List<RelatedGroup> out = new ArrayList<>();
        for (Rel r : RELATIONS) {
            if (!r.parent().equals(module)) {
                continue;
            }
            try {
                String entity = r.childClass().getSimpleName();
                long count = (long) em.createQuery(
                                "select count(e) from " + entity + " e where e." + r.fk() + ".id = :pid")
                        .setParameter("pid", id)
                        .getSingleResult();
                if (count == 0) {
                    continue;   // keep the panel tidy — no empty groups
                }
                List<?> rows = em.createQuery(
                                "select e from " + entity + " e where e." + r.fk() + ".id = :pid order by e.id desc")
                        .setParameter("pid", id)
                        .setMaxResults(ITEM_LIMIT)
                        .getResultList();
                List<RelatedItem> items = rows.stream()
                        .map(o -> (BaseEntity) o)
                        .map(e -> new RelatedItem(r.child(), e.getId(), e.feedTitle()))
                        .toList();
                out.add(new RelatedGroup(r.child(), r.label(), r.fk(), (int) count, items));
            } catch (RuntimeException skip) {
                // an unmapped FK or query issue shouldn't break the whole panel
            }
        }
        return out;
    }
}
