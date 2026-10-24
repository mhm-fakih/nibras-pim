package com.nibras.pim.web;

import com.nibras.pim.service.FulltextService;
import com.nibras.pim.service.FulltextService.BookmarkedTocResult;
import com.nibras.pim.service.FulltextService.Pending;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Consolidated view of records whose exported _record.md has unsynced edits. */
@RestController
@RequestMapping("/api/fulltext")
public class FulltextSyncController {

    private final FulltextService service;

    public FulltextSyncController(FulltextService service) {
        this.service = service;
    }

    @GetMapping("/pending")
    public List<Pending> pending() {
        return service.pending();
    }

    /**
     * Check out every bookmarked record's file (all modules) and rebuild the master TOC
     * (repo1/TOC.md) over every checkout — module → department → record, as manuscript-index embeds. Existing
     * checkouts with different content are parked with a timestamp (returned as conflicts).
     */
    @PostMapping("/bookmarked-toc")
    public BookmarkedTocResult bookmarkedToc() {
        return service.exportBookmarkedToc();
    }
}
