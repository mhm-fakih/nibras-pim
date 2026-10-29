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

    /**
     * Brings a saved search written for the old Grails app up to this one's HQL, so the reports
     * carried over keep running:
     * <ul>
     *   <li>package-qualified entity names ({@code mcs.Book}, {@code app.IndexCard},
     *       {@code mcs.parameters.SavedSearch}) lose the package — entities here are unqualified;</li>
     *   <li>{@code = null} / {@code != null} become {@code is null} / {@code is not null}
     *       (Hibernate 6 compares with a literal NULL, which is never true);</li>
     *   <li>date arithmetic in whole days ({@code current_date() - 7}) says its unit
     *       ({@code current_date - 7 day}), as Hibernate 6 requires;</li>
     *   <li>the old "group the results by" suffix ({@code ... {department}) is dropped;</li>
     *   <li>a trailing {@code max N} becomes the row cap rather than a syntax error.</li>
     * </ul>
     * Idempotent: an already-modern query comes back unchanged.
     */
    public static Modernized modernize(String query) {
        if (query == null) {
            return new Modernized(null, null);
        }
        String q = query.strip()
                .replaceAll("\\b(?:mcs|app)(?:\\.parameters)?\\.([A-Z]\\w*)", "$1")
                .replaceAll("(?i)(!=|<>)\\s*null\\b", "is not null")
                .replaceAll("(?i)(?<![<>!])=\\s*null\\b", "is null")
                .replaceAll("(?i)\\bcurrent_date(?:\\s*\\(\\s*\\))?\\s*([+-])\\s*(\\d+)\\b(?!\\s*(?:day|week|month|year|hour|minute|second))",
                        "current_date $1 $2 day")
                .replaceAll("\\s*\\{\\s*\\w+\\s*}?\\s*$", "");
        Integer max = null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)\\s+max\\s+(\\d+)\\s*$").matcher(q);
        if (m.find()) {
            max = Integer.parseInt(m.group(1));
            q = q.substring(0, m.start());
        }
        return new Modernized(q.strip(), max);
    }

    /** A {@link #modernize}d query and the row cap its {@code max N} asked for (null = none). */
    public record Modernized(String query, Integer max) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public SelectResult select(String type, String queryText) {
        Integer max = null;
        if (!"sql".equals(type)) {
            Modernized mod = modernize(queryText);
            queryText = mod.query();
            max = mod.max();
        }
        Query q = "sql".equals(type) ? em.createNativeQuery(queryText) : em.createQuery(queryText);
        q.setMaxResults(max == null ? MAX_ROWS : Math.min(max, MAX_ROWS));
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
        if (!"sql".equals(type)) {
            queryText = modernize(queryText).query();
        }
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
