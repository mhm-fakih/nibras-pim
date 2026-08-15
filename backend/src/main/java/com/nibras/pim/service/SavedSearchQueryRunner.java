package com.nibras.pim.service;

import com.nibras.pim.domain.BaseEntity;
import com.nibras.pim.domain.Book;
import com.nibras.pim.domain.Goal;
import com.nibras.pim.domain.IndexCard;
import com.nibras.pim.domain.Journal;
import com.nibras.pim.domain.Planner;
import com.nibras.pim.domain.Task;
import com.nibras.pim.domain.Writing;
import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.domain.lookup.Department;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Executes a saved search's HQL/SQL in its OWN transaction (REQUIRES_NEW), so an invalid or failing
 * user query can't poison the caller's transaction (the {@code UnexpectedRollbackException} trap).
 * Mirrors {@link IndicatorQueryRunner}. SELECTs run read-only and are capped; UPDATE/DELETE/INSERT
 * run read-write and return the affected-row count.
 */
@Service
public class SavedSearchQueryRunner {

    private static final int MAX_ROWS = 500;

    /** Entity class → module key, so an HQL result that IS a mapped entity (e.g. {@code from Task})
     *  can become a clickable record. A projection (e.g. {@code select id, code from Department})
     *  yields {@code Object[]} rows instead and stays a plain table. Mirrors BatchEntryService.CLASSES
     *  (+ departments). */
    private static final Map<Class<?>, String> MODULE_OF = Map.of(
            Task.class, "tasks", Journal.class, "journals", Planner.class, "planners",
            Book.class, "resources", IndexCard.class, "notes", Goal.class, "goals",
            Writing.class, "writings", Course.class, "courses", Department.class, "departments");

    /** Module key owning this entity class, or null if it isn't a record type. Shared with the
     *  report runner, which has the same question about a user-written query's result rows. */
    public static String moduleOf(Class<?> entityClass) {
        return MODULE_OF.get(entityClass);
    }

    /** A clickable result record: {@code {module, id, title}}. */
    public record RecordRef(String module, Long id, String title) {
    }

    /** A SELECT result: the raw rows (for the table view) plus, when every row is a mapped entity,
     *  the clickable records ({@code records} is empty otherwise). */
    public record SelectResult(List<List<Object>> rows, List<RecordRef> records) {
    }

    @PersistenceContext
    private EntityManager em;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public SelectResult select(String type, String queryText) {
        Query q = "sql".equals(type) ? em.createNativeQuery(queryText) : em.createQuery(queryText);
        q.setMaxResults(MAX_ROWS);
        List<?> results = q.getResultList();
        List<List<Object>> rows = new ArrayList<>(results.size());
        List<RecordRef> records = new ArrayList<>(results.size());
        boolean allRecords = !results.isEmpty();
        for (Object r : results) {
            rows.add(toRow(r));
            RecordRef ref = toRecordRef(r);
            if (ref == null) {
                allRecords = false;
            } else {
                records.add(ref);
            }
        }
        // Clickable records only when EVERY row is a mapped entity (a `from X`-style HQL); any
        // projection or unmapped type falls back to the plain table (records empty).
        return new SelectResult(rows, allRecords ? records : List.of());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int mutate(String type, String queryText) {
        Query q = "sql".equals(type) ? em.createNativeQuery(queryText) : em.createQuery(queryText);
        return q.executeUpdate();
    }

    private static RecordRef toRecordRef(Object r) {
        if (r instanceof BaseEntity e) {
            String module = MODULE_OF.get(Hibernate.getClass(e));   // unwrap any lazy proxy
            if (module != null) {
                return new RecordRef(module, e.getId(), e.feedTitle() == null ? "" : e.feedTitle());
            }
        }
        return null;
    }

    private static List<Object> toRow(Object r) {
        if (r instanceof Object[] arr) {
            return Arrays.asList(arr);
        }
        if (r instanceof BaseEntity e) {
            return List.of(e.getId(), e.feedTitle() == null ? "" : e.feedTitle());
        }
        List<Object> single = new ArrayList<>(1);
        single.add(r);
        return single;
    }
}
