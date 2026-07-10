package com.nibras.pim.web;

import com.nibras.pim.service.FulltextService;
import com.nibras.pim.service.FulltextService.Pending;
import org.springframework.web.bind.annotation.GetMapping;
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
}
