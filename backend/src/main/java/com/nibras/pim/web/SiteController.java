package com.nibras.pim.web;

import com.nibras.pim.domain.SavedSearch;
import com.nibras.pim.service.SavedSearchService;
import com.nibras.pim.service.SiteGeneratorService;
import com.nibras.pim.service.SiteGeneratorService.SiteResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Static-site generator: publish a saved search's records as a static website on disk. */
@RestController
@RequestMapping("/api/sites")
public class SiteController {

    private final SiteGeneratorService generator;
    private final SavedSearchService savedSearches;

    public SiteController(SiteGeneratorService generator, SavedSearchService savedSearches) {
        this.generator = generator;
        this.savedSearches = savedSearches;
    }

    /** A saved search the user can publish (only 'form'/'hql' are publishable). */
    public record PublishableSearch(Long id, String summary, String module, String entity, String queryType) {
    }

    public record SitesInfo(String outputDir, List<PublishableSearch> searches) {
    }

    @GetMapping
    public SitesInfo list() {
        List<PublishableSearch> searches = savedSearches.all().stream()
                .map(SiteController::toPublishable)
                .toList();
        return new SitesInfo(generator.configuredOutputDir(), searches);
    }

    public record GenerateRequest(String out) {
    }

    @PostMapping("/{savedSearchId}/generate")
    public SiteResult generate(@PathVariable Long savedSearchId,
                               @RequestBody(required = false) GenerateRequest body) {
        return generator.generate(savedSearchId, body == null ? null : body.out());
    }

    private static PublishableSearch toPublishable(SavedSearch s) {
        return new PublishableSearch(s.getId(), s.getSummary(),
                SavedSearchService.moduleFor(s.getEntity()), s.getEntity(), s.getQueryType());
    }
}
