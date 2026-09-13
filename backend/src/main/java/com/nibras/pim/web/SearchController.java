package com.nibras.pim.web;

import com.nibras.pim.service.OllamaService;
import com.nibras.pim.service.SearchIndexService.SearchHit;
import com.nibras.pim.service.SearchReindexService;
import com.nibras.pim.service.SearchService;
import com.nibras.pim.service.SearchService.DuplicatePair;
import com.nibras.pim.service.SemanticDedupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Global full-text search across all modules, plus a full reindex. */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService search;
    private final SearchReindexService reindex;
    private final SemanticDedupService semanticDedup;

    public SearchController(SearchService search, SearchReindexService reindex, SemanticDedupService semanticDedup) {
        this.search = search;
        this.reindex = reindex;
        this.semanticDedup = semanticDedup;
    }

    /** Rebuild outcome: rows written, rows skipped, and the first few reasons. */
    public record ReindexResult(int indexed, int skipped, java.util.List<String> failures) {
    }

    @GetMapping
    public List<SearchHit> search(@RequestParam String q,
                                  @RequestParam(defaultValue = "100") int limit) {
        // A leading ~ switches to fuzzy duplicate detection (find near-identical existing titles).
        if (q != null && q.startsWith("~")) {
            return search.duplicates(q.substring(1), Math.min(limit, 50));
        }
        return search.search(q, Math.min(limit, 200));
    }

    @PostMapping("/reindex")
    public ReindexResult reindex() {
        var report = reindex.reindexAll();
        return new ReindexResult(report.indexed(), report.skipped(), report.failures());
    }

    public record IndexStatus(long indexed) {
    }

    /**
     * How many records are in {@code search_doc}. The index is additive — it's filled incrementally
     * as records are written, and in bulk only by {@link #reindex()}. On a fresh install pointed at
     * an existing database it is therefore EMPTY, and global search silently returns nothing while
     * each module's own LIKE-based search still works. The UI reads this to say "rebuild the index"
     * instead of "no results found".
     */
    @GetMapping("/index-status")
    public IndexStatus indexStatus() {
        return new IndexStatus(reindex.indexedCount());
    }

    /** Candidate duplicate pairs already in the database, within one module — for a one-off dedup scan. */
    @GetMapping("/duplicates")
    public List<DuplicatePair> duplicatePairs(@RequestParam String module,
                                              @RequestParam(defaultValue = "50") int limit) {
        return search.duplicatePairs(module, Math.min(limit, 200));
    }

    /** Whether the local Ollama instance is reachable, and which of the two dedup models it has. */
    @GetMapping("/semantic-status")
    public OllamaService.Status semanticStatus() {
        return semanticDedup.status();
    }

    /**
     * AI-assisted duplicate scan within one module. {@code mode=embedding} is a fast, same-language
     * pass (nomic-embed-text similarity); {@code mode=llm} is a slower cross-language pass
     * (aya-expanse judges the top candidate pairs) — see {@link SemanticDedupService} for why the
     * two aren't the same pipeline.
     */
    @GetMapping("/duplicates/semantic")
    public List<DuplicatePair> semanticDuplicates(@RequestParam String module,
                                                  @RequestParam(defaultValue = "embedding") String mode,
                                                  @RequestParam(defaultValue = "20") int limit) {
        int cappedLimit = Math.min(limit, 50);
        return "llm".equals(mode)
                ? semanticDedup.llmScan(module, cappedLimit)
                : semanticDedup.embeddingScan(module, cappedLimit);
    }
}
