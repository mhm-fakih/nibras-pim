package com.nibras.pim.service;

import com.nibras.pim.domain.Book;
import com.nibras.pim.domain.Goal;
import com.nibras.pim.domain.IndexCard;
import com.nibras.pim.domain.Indicator;
import com.nibras.pim.domain.Journal;
import com.nibras.pim.domain.Planner;
import com.nibras.pim.domain.Task;
import com.nibras.pim.domain.Writing;
import com.nibras.pim.domain.lookup.Course;
import com.nibras.pim.domain.lookup.Department;
import com.nibras.pim.service.CommandParser.SearchQuery;
import com.nibras.pim.service.SearchIndexService.SearchHit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Global search that understands the shared command grammar: free-text words run through the
 * {@code search_doc} FULLTEXT index, while tokens ({@code p4 ?done #code @code * dCODE cCODE})
 * filter the hits by each record's actual fields. So {@code "p4 linux"} = priority-4 records that
 * also match "linux".
 */
@Service
public class SearchService {

    /** Every module indexed in search_doc -> its entity class (for field-level filtering). */
    private static final Map<String, Class<?>> CLASSES = Map.ofEntries(
            Map.entry("tasks", Task.class), Map.entry("journals", Journal.class),
            Map.entry("planners", Planner.class), Map.entry("resources", Book.class),
            Map.entry("notes", IndexCard.class), Map.entry("goals", Goal.class),
            Map.entry("writings", Writing.class), Map.entry("departments", Department.class),
            Map.entry("courses", Course.class), Map.entry("indicators", Indicator.class));

    private final SearchIndexService index;
    private final CommandParser parser;

    @PersistenceContext
    private EntityManager em;

    public SearchService(SearchIndexService index, CommandParser parser) {
        this.index = index;
        this.parser = parser;
    }

    /**
     * Fuzzy duplicate detection (the {@code ~} search): rank existing titles by trigram similarity
     * to the query, so "IT system hand over" surfaces an existing "IT handover". Compares normalized
     * (lowercased, alphanumeric-only) strings, which collapses "hand over" → "handover".
     */
    @Transactional(readOnly = true)
    public List<SearchHit> duplicates(String text, int limit) {
        Set<String> qGrams = trigrams(normalize(text));
        if (qGrams.isEmpty()) {
            return List.of();
        }
        List<SearchHit> scored = new ArrayList<>();
        for (SearchHit doc : index.allTitles()) {
            double sim = jaccard(qGrams, trigrams(normalize(doc.title())));
            if (sim >= 0.2) {
                scored.add(new SearchHit(doc.module(), doc.id(), doc.title(), sim, ""));
            }
        }
        scored.sort((a, b) -> Double.compare(b.score(), a.score()));
        return scored.size() > limit ? scored.subList(0, limit) : scored;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9\\p{IsAlphabetic}]", "");
    }

    private static Set<String> trigrams(String s) {
        Set<String> grams = new HashSet<>();
        if (s.length() < 3) {
            if (!s.isEmpty()) {
                grams.add(s);
            }
            return grams;
        }
        for (int i = 0; i + 3 <= s.length(); i++) {
            grams.add(s.substring(i, i + 3));
        }
        return grams;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int inter = 0;
        for (String g : a) {
            if (b.contains(g)) {
                inter++;
            }
        }
        return (double) inter / (a.size() + b.size() - inter);
    }

    @Transactional(readOnly = true)
    public List<SearchHit> search(String q, int limit) {
        SearchQuery sq = parser.parseSearch(q);
        if (!sq.hasFilters()) {
            return index.search(q, limit);   // plain full-text, unchanged
        }
        // Over-fetch, then keep only records whose fields satisfy the token filters.
        int fetch = Math.min(500, Math.max(limit, limit * 5));
        List<SearchHit> raw = sq.text().isBlank() ? index.recent(fetch) : index.search(sq.text(), fetch);

        List<SearchHit> out = new ArrayList<>();
        for (SearchHit h : raw) {
            Class<?> cls = CLASSES.get(h.module());
            if (cls == null) {
                continue;
            }
            Object entity = em.find(cls, h.id());
            if (entity != null && matches(entity, sq)) {
                out.add(h);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    private boolean matches(Object entity, SearchQuery sq) {
        BeanWrapper bw = new BeanWrapperImpl(entity);
        if (sq.priority() != null && !sq.priority().equals(read(bw, "priority"))) {
            return false;
        }
        if (sq.bookmarked() != null && sq.bookmarked() != Boolean.TRUE.equals(read(bw, "bookmarked"))) {
            return false;
        }
        return codeOk(bw, "status", sq.statusCode())
                && codeOk(bw, "type", sq.typeCode())
                && codeOk(bw, "context", sq.contextCode())
                && codeOk(bw, "department", sq.departmentCode())
                && codeOk(bw, "course", sq.courseCode());
    }

    /** True when no code is required, or the record's association has a matching code. */
    private boolean codeOk(BeanWrapper bw, String assocField, String wanted) {
        if (wanted == null) {
            return true;
        }
        Object assoc = read(bw, assocField);
        if (assoc == null) {
            return false;
        }
        Object code = read(new BeanWrapperImpl(assoc), "code");
        return code != null && wanted.equalsIgnoreCase(code.toString());
    }

    private static Object read(BeanWrapper bw, String prop) {
        return bw.isReadableProperty(prop) ? bw.getPropertyValue(prop) : null;
    }
}
