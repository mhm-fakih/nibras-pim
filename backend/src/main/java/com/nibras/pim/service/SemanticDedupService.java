package com.nibras.pim.service;

import com.nibras.pim.service.SearchIndexService.SearchHit;
import com.nibras.pim.service.SearchService.DuplicatePair;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI-assisted duplicate scan, on top of the plain trigram scan in {@link SearchService}:
 * <ul>
 *   <li>{@code embedding} mode — pairwise cosine similarity over {@code nomic-embed-text}
 *       embeddings. Cheap compared to the LLM mode (well under a minute, batched into one Ollama
 *       call), but only reliable within one language — cross-language pairs score no higher than
 *       unrelated ones with this model (measured: a genuine English/Arabic duplicate scored ~0.36,
 *       the same as noise).</li>
 *   <li>{@code llm} mode — {@code aya-expanse} judges candidate pairs for true same-meaning,
 *       including across languages. This model is slow on CPU (~10-20s per judgment), so the
 *       candidate pool sent to it is capped: the union of the top embedding-similarity pairs and
 *       the top trigram-similarity pairs (from {@link SearchService#duplicatePairs}), which covers
 *       same-language near-duplicates well; a cross-language pair with no lexical or embedding
 *       overlap at all can still be missed by the prefilter. Use the embedding mode for a full,
 *       fast same-language sweep, and the LLM mode as a slower spot-check on top candidates.
 * </ul>
 * Both modes only look at each module's {@value #TITLE_POOL_SIZE} most-recently-updated records
 * (a module here can hold tens of thousands) — a scan finds fresh duplicates, not archaeology.
 */
@Service
public class SemanticDedupService {

    private static final double EMBEDDING_DUPLICATE_THRESHOLD = 0.80;
    private static final double EMBEDDING_CANDIDATE_THRESHOLD = 0.55;
    /** How many candidate pairs get sent to the LLM per scan — bounds worst-case latency. */
    private static final int MAX_LLM_CHECKS = 20;
    /**
     * How many of a module's most-recently-updated titles get embedded per scan. A module can hold
     * many thousands of records (this app's "resources" module runs in the tens of thousands) —
     * embedding all of them, even batched, would take minutes; this keeps one scan to well under a
     * minute while covering the records most likely to be a fresh duplicate.
     */
    private static final int TITLE_POOL_SIZE = 250;

    private final SearchIndexService index;
    private final SearchService search;
    private final OllamaService ollama;

    public SemanticDedupService(SearchIndexService index, SearchService search, OllamaService ollama) {
        this.index = index;
        this.search = search;
        this.ollama = ollama;
    }

    public OllamaService.Status status() {
        return ollama.status();
    }

    /** Pure embedding-similarity duplicate pairs within one module — fast, same-language. */
    public List<DuplicatePair> embeddingScan(String module, int limit) {
        List<DuplicatePair> pairs = embeddingPairs(module, EMBEDDING_DUPLICATE_THRESHOLD);
        return pairs.size() > limit ? pairs.subList(0, limit) : pairs;
    }

    /**
     * AI-judged duplicate pairs within one module: aya-expanse confirms each candidate from the
     * combined trigram + embedding candidate pool. Slow — see class docs.
     */
    public List<DuplicatePair> llmScan(String module, int limit) {
        Map<String, DuplicatePair> candidates = new LinkedHashMap<>();
        for (DuplicatePair p : search.duplicatePairs(module, MAX_LLM_CHECKS)) {
            candidates.put(pairKey(p.id1(), p.id2()), p);
        }
        for (DuplicatePair p : embeddingPairs(module, EMBEDDING_CANDIDATE_THRESHOLD)) {
            candidates.merge(pairKey(p.id1(), p.id2()), p,
                    (a, b) -> a.score() >= b.score() ? a : b);
        }
        List<DuplicatePair> pool = candidates.values().stream()
                .sorted(Comparator.comparingDouble(DuplicatePair::score).reversed())
                .limit(MAX_LLM_CHECKS)
                .toList();

        List<DuplicatePair> confirmed = new ArrayList<>();
        for (DuplicatePair c : pool) {
            if (ollama.sameMeaning(c.title1(), c.title2())) {
                confirmed.add(c);
            }
        }
        return confirmed.size() > limit ? confirmed.subList(0, limit) : confirmed;
    }

    private List<DuplicatePair> embeddingPairs(String module, double threshold) {
        // A blank/null title in the batch breaks the whole Ollama request (and carries no dedup
        // signal anyway), so drop those before embedding rather than letting one bad record fail
        // the entire scan.
        List<SearchHit> titles = index.recentTitles(module, TITLE_POOL_SIZE).stream()
                .filter(h -> h.title() != null && !h.title().isBlank())
                .toList();
        List<float[]> embeddings = ollama.embedBatch(titles.stream().map(SearchHit::title).toList());
        List<Embedded> vectors = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            vectors.add(new Embedded(titles.get(i), embeddings.get(i)));
        }
        List<DuplicatePair> pairs = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            for (int j = i + 1; j < vectors.size(); j++) {
                double sim = cosine(vectors.get(i).vector, vectors.get(j).vector);
                if (sim >= threshold) {
                    pairs.add(new DuplicatePair(module,
                            vectors.get(i).hit.id(), vectors.get(i).hit.title(),
                            vectors.get(j).hit.id(), vectors.get(j).hit.title(), sim));
                }
            }
        }
        pairs.sort(Comparator.comparingDouble(DuplicatePair::score).reversed());
        return pairs;
    }

    private static String pairKey(long id1, long id2) {
        return Math.min(id1, id2) + ":" + Math.max(id1, id2);
    }

    private record Embedded(SearchHit hit, float[] vector) {
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
