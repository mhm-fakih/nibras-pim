package com.nibras.pim.web;

import com.nibras.pim.service.UrlScrapeService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * URL → prefilled Resource form.
 *
 * <p>Deliberately a POST even though it reads: it makes the server issue an outbound request to a
 * URL the caller chose, which is a side effect worth gating. SecurityConfig requires
 * {@code content.write} for every POST under {@code /api/**}, so only users who could create the
 * resource anyway can make the server fetch on their behalf — a GET would have been reachable by
 * any authenticated user, including READ_ONLY.
 */
@RestController
@RequestMapping("/api/scrape")
public class ScrapeController {

    private final UrlScrapeService scraper;

    public ScrapeController(UrlScrapeService scraper) {
        this.scraper = scraper;
    }

    public record ScrapeRequest(String url) {
    }

    @PostMapping
    public UrlScrapeService.Scraped scrape(@RequestBody ScrapeRequest body) {
        try {
            return scraper.scrape(body == null ? null : body.url());
        } catch (IllegalArgumentException e) {
            // Every rejection reason is meant for the user — a bad URL, a private address, a dead
            // page — so surface the message rather than a generic 500.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }
}
