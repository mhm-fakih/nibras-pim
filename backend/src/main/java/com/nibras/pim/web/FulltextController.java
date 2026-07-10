package com.nibras.pim.web;

import com.nibras.pim.service.FulltextService;
import com.nibras.pim.service.FulltextService.ExportResult;
import com.nibras.pim.service.FulltextService.FieldDiff;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Export a record's editable long-text fields to a single Markdown file for editing in any
 * text editor, then review and apply the changes back.
 */
@RestController
@RequestMapping("/api/{module}/{id}/fulltext")
public class FulltextController {

    private final FulltextService service;

    public FulltextController(FulltextService service) {
        this.service = service;
    }

    /**
     * Write/refresh the record's _record.md and return its path + content. {@code open}
     * forces the auto-open behaviour (true/false); when omitted, the {@code export.open}
     * setting decides.
     */
    @PostMapping("/export")
    public ExportResult export(@PathVariable String module, @PathVariable long id,
                               @RequestParam(required = false) Boolean open) {
        return service.export(module, id, open);
    }

    /** Changed fields between the on-disk file and the DB (empty = nothing to sync). */
    @GetMapping("/changes")
    public List<FieldDiff> changes(@PathVariable String module, @PathVariable long id) {
        return service.changes(module, id);
    }

    /** Apply the file's changes to the record; returns the fields that were updated. */
    @PostMapping("/apply")
    public List<FieldDiff> apply(@PathVariable String module, @PathVariable long id) {
        return service.apply(module, id);
    }
}
