package com.nibras.pim.service;

import com.nibras.pim.domain.BaseEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Runs a report's HQL — {@code from Book where type.code = 'art' order by id desc} — a page at a
 * time.
 *
 * <p>Like {@link SavedSearchQueryRunner} this runs in its OWN transaction (REQUIRES_NEW) so a
 * malformed user query can't poison the caller's transaction, and read-only so nothing a report
 * does can write. Unlike the saved-search runner it refuses DML outright: a saved search is an
 * admin tool that may legitimately be an UPDATE, whereas a report is a thing you *look at*, and
 * "open the report" must never be a way to change data.
 *
 * <p>It also differs in what it returns. The saved-search runner flattens each row to
 * {@code {module, id, title}}; a report needs the whole record, because its columns are the
 * module's own fields and its lookups have to arrive as {@code {id, name, code}} for a type column
 * to read "Article". So the entities come back intact and the caller maps them through the module's
 * own DTO.
 */
@Service
public class ReportQueryRunner {

    /** Anything that writes. Checked on the query text because a read-only transaction won't stop
     *  HQL bulk DML — Hibernate happily executes {@code delete from Book} inside one. */
    private static final Pattern WRITES = Pattern.compile(
            "^\\s*(update|delete|insert|merge)\\b", Pattern.CASE_INSENSITIVE);

    /** The trailing {@code order by} clause, so a count query can drop it. */
    private static final Pattern TRAILING_ORDER_BY = Pattern.compile(
            "\\s+order\\s+by\\s+[^)]*$", Pattern.CASE_INSENSITIVE);

    /**
     * One page of results.
     *
     * @param rows  this page, already mapped by the caller's {@code mapper}
     * @param total how many rows the query returns in all, or {@code -1} when that can't be worked
     *              out — see {@link #countOf}. Callers must not present -1 as a number.
     */
    public record Window(List<Object> rows, long total) {
    }

    @PersistenceContext
    private EntityManager em;

    /**
     * Run {@code hql} and hand each row to {@code mapper}.
     *
     * <p>The mapper runs HERE, inside this transaction, rather than on the entities being returned
     * to the caller — REQUIRES_NEW closes its persistence context on the way out, so anything left
     * unread (a lazy {@code tags} collection, say) would be a {@code LazyInitializationException}
     * the moment the caller built its DTO. Mapping inside is the difference between a report that
     * works and one that fails on every record that has a tag.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Window run(String hql, int offset, int limit, Function<BaseEntity, Object> mapper) {
        String text = validate(hql);

        Query q;
        try {
            q = em.createQuery(text);
        } catch (RuntimeException badQuery) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, explain(text, badQuery), badQuery);
        }
        q.setFirstResult(Math.max(0, offset));
        q.setMaxResults(Math.max(1, limit));

        List<?> results;
        try {
            results = q.getResultList();
        } catch (RuntimeException failed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, explain(text, failed), failed);
        }

        List<Object> rows = new ArrayList<>(results.size());
        for (Object r : results) {
            if (!(r instanceof BaseEntity e)) {
                // A projection (`select title, author from Book`) has no record behind each row, so
                // there is nothing to render a cover or a status chip from. Reports are built on
                // records; say so rather than showing a table of nulls.
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A report query must return whole records — start it with \"from …\", "
                                + "not \"select <fields> from …\". Choose which fields to show with the columns above.");
            }
            rows.add(mapper.apply(e));
        }
        return new Window(rows, countOf(text));
    }

    /** The query text, rejected if it writes. */
    private String validate(String hql) {
        String text = hql == null ? "" : hql.trim();
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The report has no query.");
        }
        if (WRITES.matcher(text).find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A report only reads. Start the query with \"from …\".");
        }
        if (text.contains(";")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A report runs one query — remove the \";\".");
        }
        return text;
    }

    /**
     * How many rows the query returns in all, or -1 if that can't be established.
     *
     * <p>Derived by rewriting {@code from X where …} into {@code select count(*) from X where …}
     * with the ordering dropped. That rewrite is textual and therefore fallible — a subquery
     * containing its own {@code order by}, say — so a count that doesn't parse or doesn't run
     * returns -1 instead of failing the report. A page of rows you can read beats an exact total
     * you can't.
     */
    private long countOf(String text) {
        String body = TRAILING_ORDER_BY.matcher(text).replaceAll("");
        if (!body.toLowerCase(Locale.ROOT).startsWith("from ")) {
            return -1;
        }
        try {
            Object one = em.createQuery("select count(*) " + body).getSingleResult();
            return one instanceof Number n ? n.longValue() : -1;
        } catch (RuntimeException uncountable) {
            return -1;
        }
    }

    /** The root cause of a query failure, phrased for someone who typed the query. */
    private String explain(String text, RuntimeException e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String hint = text.toLowerCase(Locale.ROOT).contains(" sort by ")
                ? " (HQL orders with \"order by\", not \"sort by\".)"
                : "";
        return "That query didn't run: " + root.getMessage() + hint;
    }
}
