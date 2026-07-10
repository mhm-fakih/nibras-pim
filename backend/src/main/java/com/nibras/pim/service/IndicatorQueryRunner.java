package com.nibras.pim.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs an indicator's stored query. Each attempt executes in its OWN read-only transaction, so a
 * failing/invalid query can neither poison the surrounding {@code due()} transaction nor poison the
 * native-SQL fallback (a Hibernate HQL parse error marks the active transaction rollback-only).
 *
 * <p>Legacy indicators store <b>HQL</b> ({@code from Task}, {@code from Writing where type.id != 22})
 * whose value is the <b>count of matching rows</b>; others store a native scalar
 * ({@code SELECT COUNT(*) FROM task WHERE …}). This handles all three shapes:
 * <ul>
 *   <li>{@code from X [where …]} → {@code select count(*) from X …} (efficient row count).</li>
 *   <li>any other HQL (e.g. {@code select count(*) from Task}) → first scalar of the result.</li>
 *   <li>not valid HQL → run as native SQL and read the first scalar.</li>
 * </ul>
 */
@Service
public class IndicatorQueryRunner {

    @PersistenceContext
    private EntityManager em;

    /** Self proxy so each helper runs in its own REQUIRES_NEW transaction (isolated rollback). */
    private final ObjectProvider<IndicatorQueryRunner> self;

    public IndicatorQueryRunner(ObjectProvider<IndicatorQueryRunner> self) {
        this.self = self;
    }

    /** Not transactional itself — dispatches to helpers, each in its own transaction. */
    public Double run(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String q = query.trim();
        // "from X [where …]" is an HQL entity selection — its value is the number of matching rows.
        if (q.toLowerCase().startsWith("from ")) {
            return self.getObject().runHqlCount(q);
        }
        // Otherwise try HQL (e.g. an explicit "select count(*) …") in its own transaction; if that
        // fails the query is native SQL — run it in a fresh transaction so the HQL parse error
        // (which marked its transaction rollback-only) can't poison this fallback.
        try {
            return self.getObject().runHqlScalar(q);
        } catch (RuntimeException notHql) {
            return self.getObject().runNativeScalar(q);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Double runHqlCount(String q) {
        return IndicatorMonitorService.toDouble(
                em.createQuery("select count(*) " + normalizeHql(q)).getSingleResult());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Double runHqlScalar(String q) {
        Object r = em.createQuery(normalizeHql(q)).getResultList().stream().findFirst().orElse(null);
        return IndicatorMonitorService.toDouble(r);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Double runNativeScalar(String q) {
        Object row = em.createNativeQuery(q).getResultList().stream().findFirst().orElse(null);
        return IndicatorMonitorService.toDouble(row);
    }

    /**
     * Modernize legacy HQL null comparisons: old Grails/Hibernate treated {@code = null} as
     * {@code is null}, but Hibernate 6 compares against the literal NULL (never true). Rewrite
     * {@code = null} / {@code != null} so queries like {@code where deletedOn = null} still work.
     */
    private static String normalizeHql(String q) {
        return q.replaceAll("(?i)(!=|<>)\\s*null", " is not null")
                .replaceAll("(?i)=\\s*null", " is null");
    }
}
