package com.nibras.pim.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maintains and queries the {@code search_doc} FULLTEXT index. Pure JDBC (no entity
 * dependencies) so it can be injected into the CRUD services without a bean cycle.
 */
@Service
public class SearchIndexService {

    private final JdbcTemplate jdbc;

    public SearchIndexService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record SearchHit(String module, long id, String title, double score, String snippet) {
    }

    /** One record as the index stores it — no entity types, so this class stays cycle-free. */
    public record Doc(Long id, String title, String body) {
    }

    /**
     * Write a batch of docs in a transaction of its own.
     *
     * <p>{@code REQUIRES_NEW} is the point of the method: a rebuild writes ~50k rows, and a batch
     * the database refuses must roll back only itself rather than mark an enclosing transaction
     * rollback-only and discard everything indexed so far. Called from
     * {@link SearchReindexService}, which is a different bean — a self-call would bypass the proxy
     * and silently give no transaction at all.
     */
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public int upsertAll(String module, List<Doc> docs) {
        for (Doc d : docs) {
            upsert(module, d.id(), d.title(), d.body());
        }
        return docs.size();
    }

    public void upsert(String module, Long id, String title, String body) {
        if (id == null) {
            return;
        }
        jdbc.update("""
                insert into search_doc (module, record_id, title, body, updated_at)
                values (?, ?, ?, ?, ?)
                on duplicate key update title = values(title), body = values(body),
                                        updated_at = values(updated_at)
                """, module, id, title, body, Timestamp.valueOf(LocalDateTime.now()));
    }

    public void delete(String module, Long id) {
        if (id != null) {
            jdbc.update("delete from search_doc where module = ? and record_id = ?", module, id);
        }
    }

    /** Rows in the index. 0 means "never built" — see SearchController.indexStatus(). */
    public long count() {
        Long n = jdbc.queryForObject("select count(*) from search_doc", Long.class);
        return n == null ? 0L : n;
    }

    /**
     * Natural-language full-text search across all modules, best matches first. MySQL's natural-
     * language relevance score is pure word-frequency math over the combined title+body index —
     * it has no idea a record's TITLE literally contains the whole query as a phrase, so a record
     * whose body happens to repeat the individual (common) words more often can outrank one whose
     * title matches the query exactly. An exact-phrase title match is exactly what a searcher means
     * by "the right answer", so those are boosted to the very top before falling back to the normal
     * relevance score as the tiebreaker.
     */
    public List<SearchHit> search(String q, int limit) {
        return search(q, limit, true, true);
    }

    /**
     * Full-text search over the title, the body, or both. Each choice has a FULLTEXT index of its
     * own (ft_title, ft_body, ft_search — see V13), because MATCH() has to name exactly the columns
     * of one index.
     */
    public List<SearchHit> search(String q, int limit, boolean inTitle, boolean inBody) {
        if (q == null || q.isBlank() || (!inTitle && !inBody)) {
            return List.of();
        }
        String cols = inTitle && inBody ? "title, body" : inTitle ? "title" : "body";
        List<String> terms = terms(q);
        return jdbc.query("""
                select module, record_id,
                       title, body,
                       match(%1$s) against (? in natural language mode) as score,
                       (title like concat('%%', ?, '%%')) as title_exact
                from search_doc
                where match(%1$s) against (? in natural language mode)
                order by title_exact desc, score desc
                limit ?
                """.formatted(cols),
                (rs, i) -> new SearchHit(rs.getString("module"), rs.getLong("record_id"),
                        rs.getString("title"), rs.getDouble("score"),
                        snippet(rs.getString("body"), terms)),
                q, q, q, limit);
    }

    /** Every indexed doc's id + title (no body) — used by the fuzzy duplicate search. */
    public List<SearchHit> allTitles() {
        return jdbc.query("select module, record_id, title from search_doc",
                (rs, i) -> new SearchHit(rs.getString("module"), rs.getLong("record_id"),
                        rs.getString("title"), 0.0, ""));
    }

    /**
     * A module's most-recently-updated titles, capped — used by the semantic dedup scan, which
     * has to call out to an embedding model per title and so can't afford to run over an entire
     * module of thousands of records. Most-recent-first is also the more useful subset for "did I
     * just re-enter this" than an arbitrary slice would be.
     */
    public List<SearchHit> recentTitles(String module, int limit) {
        return jdbc.query("""
                select record_id, title
                from search_doc
                where module = ?
                order by updated_at desc
                limit ?
                """,
                (rs, i) -> new SearchHit(module, rs.getLong("record_id"), rs.getString("title"), 0.0, ""),
                module, limit);
    }

    /** Most-recently-updated docs (no full-text term) — used when a search has only token filters. */
    public List<SearchHit> recent(int limit) {
        return jdbc.query("""
                select module, record_id, title, body
                from search_doc
                order by updated_at desc
                limit ?
                """,
                (rs, i) -> new SearchHit(rs.getString("module"), rs.getLong("record_id"),
                        rs.getString("title"), 0.0, snippet(rs.getString("body"), List.of())),
                limit);
    }

    private static final int MAX_LINES = 3;

    /** Significant query terms — see {@link Snippets#terms}. */
    private static List<String> terms(String q) {
        return Snippets.terms(q);
    }

    /**
     * Up to {@link #MAX_LINES} short excerpts of body lines that contain a query term.
     *
     * <p>Delegated so the list pane's per-record match context and these results cut text the same
     * way; see {@link Snippets}.
     */
    private static String snippet(String body, List<String> terms) {
        return Snippets.fromLines(body, terms, MAX_LINES);
    }
}
