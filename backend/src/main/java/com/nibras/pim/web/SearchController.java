package com.nibras.pim.web;

import com.nibras.pim.service.OllamaService;
import com.nibras.pim.service.SearchIndexService.SearchHit;
import com.nibras.pim.service.SearchReindexService;
import com.nibras.pim.service.SearchService;
import com.nibras.pim.service.SearchService.DuplicatePair;
import com.nibras.pim.service.SemanticDedupService;
import com.nibras.pim.service.localnotes.LocalNotesService;
import com.nibras.pim.web.dto.LocalNoteDto;
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
    private final LocalNotesService localNotes;

    public SearchController(SearchService search, SearchReindexService reindex,
                            SemanticDedupService semanticDedup, LocalNotesService localNotes) {
        this.search = search;
        this.reindex = reindex;
        this.semanticDedup = semanticDedup;
        this.localNotes = localNotes;
    }

    /**
     * Records AND the markdown notes on disk, for one query.
     *
     * <p>Two lists rather than one merged one, deliberately. A record and a note file are not the
     * same kind of thing — one has an id, a module and a status, the other has a file name and a
     * folder — and flattening them would mean inventing an id for a file and a score for a
     * substring match. The caller gets both halves of the answer in one request and can show them
     * side by side without either having to pretend to be the other.
     *
     * <p>A folder that cannot be read is not an error: the record half of the search is still
     * worth returning, so a missing notes folder yields an empty note list and a note saying so.
     */
    @GetMapping("/all")
    public Consolidated searchAll(@RequestParam String q,
                                  @RequestParam(defaultValue = "100") int limit) {
        int capped = Math.min(limit, 200);
        List<SearchHit> records = search(q, capped);
        List<LocalNoteDto> notes;
        String notesError = null;
        try {
            notes = localNotes.search(q, capped);
        } catch (RuntimeException e) {
            notes = List.of();
            notesError = e.getMessage();
        }
        return new Consolidated(records, notes, localNotes.dir().toString(), notesError);
    }

    public record Consolidated(List<SearchHit> records, List<LocalNoteDto> notes,
                               String notesDir, String notesError) {
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
