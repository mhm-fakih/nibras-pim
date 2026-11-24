package com.nibras.pim.web;

import com.nibras.pim.service.ExcerptLibraryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** The excerpt library: random 2–3 page samples of the ebk books, to summarize and promote. */
@RestController
@RequestMapping("/api/excerpt-library")
public class ExcerptLibraryController {

    private final ExcerptLibraryService service;

    public ExcerptLibraryController(ExcerptLibraryService service) {
        this.service = service;
    }

    public record Overview(ExcerptLibraryService.Counts counts, ExcerptLibraryService.Job job) {
    }

    @GetMapping("/overview")
    public Overview overview() {
        return new Overview(service.counts(), service.job());
    }

    @GetMapping
    public List<ExcerptLibraryService.Row> list(@RequestParam(defaultValue = "new") String status,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "500") int limit) {
        return service.list(status, q, limit);
    }

    @GetMapping("/{id}")
    public ExcerptLibraryService.Detail detail(@PathVariable long id) {
        return service.detail(id);
    }

    public record Edit(String summary, String description) {
    }

    @PutMapping("/{id}")
    public ExcerptLibraryService.Detail save(@PathVariable long id, @RequestBody Edit body) {
        return service.save(id, body.summary(), body.description());
    }

    @PostMapping("/{id}/skip")
    public ExcerptLibraryService.Detail skip(@PathVariable long id) {
        return service.setStatus(id, "skipped");
    }

    @PostMapping("/{id}/restore")
    public ExcerptLibraryService.Detail restore(@PathVariable long id) {
        return service.setStatus(id, "new");
    }

    @PostMapping("/{id}/another")
    public ExcerptLibraryService.Detail another(@PathVariable long id) {
        return service.another(id);
    }

    @PostMapping("/{id}/promote")
    public ExcerptLibraryService.Detail promote(@PathVariable long id) {
        return service.promote(id);
    }

    public record Generate(Integer count) {
    }

    /** Makes {@code count} more samples (default 50) in the background. */
    @PostMapping("/generate")
    public ExcerptLibraryService.Job generate(@RequestBody(required = false) Generate body) {
        return service.start(body == null || body.count() == null ? 50 : body.count());
    }
}
