package com.nibras.pim.web;

import com.nibras.pim.service.SearchIndexService.SearchHit;
import com.nibras.pim.service.SearchReindexService;
import com.nibras.pim.service.SearchService;
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

    public SearchController(SearchService search, SearchReindexService reindex) {
        this.search = search;
        this.reindex = reindex;
    }

    public record ReindexResult(int indexed) {
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
        return new ReindexResult(reindex.reindexAll());
    }
}
