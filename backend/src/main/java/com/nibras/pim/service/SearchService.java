package com.nibras.pim.service;

import com.nibras.pim.domain.BaseEntity;
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
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
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

    public record DuplicatePair(String module, long id1, String title1, long id2, String title2, double score) {
    }

    /** O(n²) title-pair comparison only stays "fast" up to a few thousand titles; a module here can
     * hold tens of thousands (e.g. resources), where the raw pair count itself (~n²/2 jaccard calls)
     * turns a one-off scan into a multi-minute one regardless of how cheap each call is. */
    private static final int DUPLICATE_SCAN_POOL_SIZE = 3000;

    /**
     * Candidate duplicate pairs already in the database, within one module — same trigram
     * similarity as {@link #duplicates}, just compared record-to-record instead of query-to-record.
     * Looks at the module's most-recently-updated {@value #DUPLICATE_SCAN_POOL_SIZE} titles; fine
     * as an occasional one-shot scan, not a live filter.
     */
    @Transactional(readOnly = true)
    public List<DuplicatePair> duplicatePairs(String module, int limit) {
        List<SearchHit> titles = index.recentTitles(module, DUPLICATE_SCAN_POOL_SIZE);
        // Trigram sets computed once per title, not once per pair — this module can hold tens of
        // thousands of records, and the inner loop alone re-normalizing/re-tokenizing the same
        // title for every (i, j) it appears in turns a cheap O(n) step into a redundant O(n²) one.
        List<Set<String>> grams = titles.stream().map(t -> trigrams(normalize(t.title()))).toList();
        List<DuplicatePair> pairs = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            if (grams.get(i).isEmpty()) {
                continue;
            }
            for (int j = i + 1; j < titles.size(); j++) {
                double sim = jaccard(grams.get(i), grams.get(j));
                if (sim >= 0.2) {
                    pairs.add(new DuplicatePair(module, titles.get(i).id(), titles.get(i).title(),
                            titles.get(j).id(), titles.get(j).title(), sim));
                }
            }
        }
        pairs.sort((a, b) -> Double.compare(b.score(), a.score()));
        return pairs.size() > limit ? pairs.subList(0, limit) : pairs;
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
        // A purely numeric query means "find the record whose id is this", not "find this number
        // inside the text" — ids aren't unique across modules, so return every module's match
        // (usually exactly one). Falls back to normal search if no record anywhere has that id
        // (e.g. the query was actually a year or phone-number fragment, not an id).
        String trimmed = q == null ? "" : q.trim();
        if (trimmed.matches("\\d+")) {
            List<SearchHit> byId = searchById(trimmed, limit);
            if (!byId.isEmpty()) {
                return byId;
            }
        }
        SearchQuery sq = parser.parseSearch(q);
        if (!sq.hasFilters()) {
            return index.search(q, limit);   // plain full-text, unchanged
        }
        // A pure token query (e.g. "* #quote", no free text) has to query each module's fields
        // directly: filtering the "recently updated" window (as the mixed text+token path below
        // does) would miss any match that simply hasn't been touched recently — which is exactly
        // why "* #quote" used to come back empty even with matching bookmarked/type=quote notes.
        if (sq.text().isBlank()) {
            return directFilter(sq, limit);
        }
        // Over-fetch, then keep only records whose fields satisfy the token filters.
        int fetch = Math.min(500, Math.max(limit, limit * 5));
        List<SearchHit> raw = index.search(sq.text(), fetch);

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

    /**
     * Queries every module directly by field (bookmarked/urgent/priority/type/status/context/
     * department/course code), for a token-only search with no free text to narrow the candidate
     * pool first. A module missing one of the requested fields entirely (e.g. {@code @context} on
     * Book, which has no context) can never match and is skipped outright, rather than silently
     * ignoring that half of the filter for that module.
     */
    private List<SearchHit> directFilter(SearchQuery sq, int limit) {
        record Ranked(SearchHit hit, java.time.LocalDateTime lastUpdated) {
        }
        List<Ranked> out = new ArrayList<>();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        for (Map.Entry<String, Class<?>> e : CLASSES.entrySet()) {
            String module = e.getKey();
            Class<?> cls = e.getValue();
            if (!(BaseEntity.class.isAssignableFrom(cls))) {
                continue;
            }
            @SuppressWarnings("unchecked")
            CriteriaQuery<Object> cq = (CriteriaQuery<Object>) cb.createQuery((Class<Object>) cls);
            Root<?> root = cq.from(cls);

            if ((sq.bookmarked() != null && !has(root, "bookmarked"))
                    || (sq.urgent() != null && !has(root, "urgent"))
                    || (sq.priority() != null && !has(root, "priority"))
                    || (sq.typeCode() != null && !has(root, "type"))
                    || (sq.statusCode() != null && !has(root, "status"))
                    || (sq.contextCode() != null && !has(root, "context"))
                    || (sq.departmentCode() != null && !has(root, "department"))
                    || (sq.courseCode() != null && !has(root, "course"))
                    || (sq.language() != null && !has(root, "language"))) {
                continue;   // this module can't satisfy every requested filter — skip it entirely
            }

            List<Predicate> ps = new ArrayList<>();
            if (sq.bookmarked() != null) {
                ps.add(cb.equal(root.get("bookmarked"), sq.bookmarked()));
            }
            if (sq.urgent() != null) {
                ps.add(cb.equal(root.get("urgent"), sq.urgent()));
            }
            if (sq.priority() != null) {
                ps.add(cb.equal(root.get("priority"), sq.priority()));
            }
            addCodePredicate(root, cb, ps, "type", sq.typeCode());
            addCodePredicate(root, cb, ps, "status", sq.statusCode());
            addCodePredicate(root, cb, ps, "context", sq.contextCode());
            addCodePredicate(root, cb, ps, "department", sq.departmentCode());
            addCodePredicate(root, cb, ps, "course", sq.courseCode());
            if (sq.language() != null) {
                // A plain column, not an association — compared case-insensitively because the
                // column is free text and older records may hold "AR" where newer ones hold "ar".
                ps.add(cb.equal(cb.lower(root.get("language").as(String.class)), sq.language()));
            }
            if (ps.isEmpty()) {
                continue;   // no filter actually applies to this module — nothing to search here
            }

            cq.select(root.get("id")).where(ps.toArray(new Predicate[0]));
            if (has(root, "lastUpdated")) {
                cq.orderBy(cb.desc(root.get("lastUpdated")));
            }
            List<?> ids = em.createQuery(cq).setMaxResults(limit).getResultList();
            for (Object idObj : ids) {
                Object entity = em.find(cls, idObj);
                if (entity instanceof BaseEntity be) {
                    out.add(new Ranked(new SearchHit(module, be.getId(), be.feedTitle(), 0.0, ""),
                            be.getLastUpdated()));
                }
            }
        }
        out.sort(Comparator.comparing(Ranked::lastUpdated, Comparator.nullsLast(Comparator.reverseOrder())));
        List<SearchHit> hits = out.stream().map(Ranked::hit).toList();
        return hits.size() > limit ? hits.subList(0, limit) : hits;
    }

    /** Looks up {@code id} in every indexed module directly (by primary key, not full-text) — the
     *  same {@code em.find} used by {@link #directFilter} for its per-id candidate lookups. */
    private List<SearchHit> searchById(String digits, int limit) {
        long id;
        try {
            id = Long.parseLong(digits);
        } catch (NumberFormatException overflow) {
            return List.of();
        }
        List<SearchHit> out = new ArrayList<>();
        for (Map.Entry<String, Class<?>> e : CLASSES.entrySet()) {
            Object entity = em.find(e.getValue(), id);
            if (entity instanceof BaseEntity be) {
                out.add(new SearchHit(e.getKey(), be.getId(), be.feedTitle(), 1.0, ""));
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    /** Adds an equals-code predicate on {@code root.<assocField>.code} when {@code wanted} is set. */
    private static void addCodePredicate(Root<?> root, CriteriaBuilder cb, List<Predicate> ps,
                                         String assocField, String wanted) {
        if (wanted != null) {
            ps.add(cb.equal(cb.lower(root.get(assocField).get("code")), wanted.toLowerCase()));
        }
    }

    private static boolean has(Root<?> root, String attr) {
        try {
            root.get(attr);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean matches(Object entity, SearchQuery sq) {
        BeanWrapper bw = new BeanWrapperImpl(entity);
        if (sq.priority() != null && !sq.priority().equals(read(bw, "priority"))) {
            return false;
        }
        if (sq.bookmarked() != null && sq.bookmarked() != Boolean.TRUE.equals(read(bw, "bookmarked"))) {
            return false;
        }
        if (sq.urgent() != null && sq.urgent() != Boolean.TRUE.equals(read(bw, "urgent"))) {
            return false;
        }
        if (sq.language() != null) {
            Object lang = read(bw, "language");
            if (lang == null || !sq.language().equalsIgnoreCase(String.valueOf(lang))) {
                return false;
            }
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
