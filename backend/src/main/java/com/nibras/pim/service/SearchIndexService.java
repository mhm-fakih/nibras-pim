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

    /** Natural-language full-text search across all modules, best matches first. */
    public List<SearchHit> search(String q, int limit) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        List<String> terms = terms(q);
        return jdbc.query("""
                select module, record_id,
                       title, body,
                       match(title, body) against (? in natural language mode) as score
                from search_doc
                where match(title, body) against (? in natural language mode)
                order by score desc
                limit ?
                """,
                (rs, i) -> new SearchHit(rs.getString("module"), rs.getLong("record_id"),
                        rs.getString("title"), rs.getDouble("score"),
                        snippet(rs.getString("body"), terms)),
                q, q, limit);
    }

    /** Every indexed doc's id + title (no body) — used by the fuzzy duplicate search. */
    public List<SearchHit> allTitles() {
        return jdbc.query("select module, record_id, title from search_doc",
                (rs, i) -> new SearchHit(rs.getString("module"), rs.getLong("record_id"),
                        rs.getString("title"), 0.0, ""));
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

    private static final int SNIPPET_LEN = 180;
    private static final int MAX_LINES = 3;
    private static final Pattern TERM = Pattern.compile("\\p{L}[\\p{L}\\p{N}]+");

    /** Significant query terms (lowercased words of length ≥ 2) for snippet matching. */
    private static List<String> terms(String q) {
        List<String> out = new ArrayList<>();
        Matcher m = TERM.matcher(q.toLowerCase());
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    /** Up to {@link #MAX_LINES} short excerpts of body lines that contain a query term. */
    private static String snippet(String body, List<String> terms) {
        if (body == null || body.isBlank()) {
            return "";
        }
        List<String> hits = new ArrayList<>();
        for (String raw : body.split("\\r?\\n")) {
            String line = raw.strip().replaceAll("\\s+", " ");
            if (line.isEmpty()) {
                continue;
            }
            int idx = firstTermIndex(line.toLowerCase(), terms);
            if (idx >= 0) {
                hits.add(window(line, idx));
                if (hits.size() >= MAX_LINES) {
                    break;
                }
            }
        }
        if (hits.isEmpty()) {
            // Match was in the title only — show the start of the body.
            String t = body.strip().replaceAll("\\s+", " ");
            return t.length() > SNIPPET_LEN ? t.substring(0, SNIPPET_LEN) + " …" : t;
        }
        return String.join(" … ", hits);
    }

    private static int firstTermIndex(String lowerLine, List<String> terms) {
        int best = -1;
        for (String t : terms) {
            int p = lowerLine.indexOf(t);
            if (p >= 0 && (best < 0 || p < best)) {
                best = p;
            }
        }
        return best;
    }

    /** A ~SNIPPET_LEN window of {@code line} centred a little before {@code matchIdx}. */
    private static String window(String line, int matchIdx) {
        int start = Math.max(0, matchIdx - 50);
        int end = Math.min(line.length(), start + SNIPPET_LEN);
        String s = line.substring(start, end).strip();
        if (start > 0) {
            s = "… " + s;
        }
        if (end < line.length()) {
            s = s + " …";
        }
        return s;
    }
}
