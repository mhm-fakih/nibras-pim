package com.nibras.pim.web;

import com.nibras.pim.service.FulltextService;
import com.nibras.pim.service.FulltextService.Pending;
import com.nibras.pim.service.FulltextService.WritingsTocResult;
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
     * Export all bookmarked writings' _record.md files and rebuild the master TOC
     * (repo1/W/TOC.md) — department → course → writing, as manuscript-index embeds.
     */
    @PostMapping("/writings-toc")
    public WritingsTocResult writingsToc() {
        return service.exportWritingsToc();
    }
}
