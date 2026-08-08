package com.nibras.pim.web;

import com.nibras.pim.service.RssImportService;
import com.nibras.pim.service.RssImportService.ImportResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** One-click import of a feed item (e.g. from an RSS reader) as a "resources" record. */
@RestController
@RequestMapping("/api/import/rss")
public class RssImportController {

    private final RssImportService service;

    public RssImportController(RssImportService service) {
        this.service = service;
    }

    @PostMapping
    public ImportResult importOne(@RequestBody ImportRequest request) {
        return service.importOne(request.title(), request.link(), request.description(), request.publishedOn());
    }

    public record ImportRequest(String title, String link, String description, LocalDateTime publishedOn) {
    }
}
