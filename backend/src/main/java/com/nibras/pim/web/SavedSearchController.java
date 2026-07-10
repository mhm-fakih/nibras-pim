package com.nibras.pim.web;

import com.nibras.pim.domain.SavedSearch;
import com.nibras.pim.service.SavedSearchService;
import com.nibras.pim.web.dto.SavedSearchDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

/**
 * CRUD + execution for user-defined reports (saved searches). Lets the user add/edit/remove reports
 * from the UI and run them on demand. Mutating queries are permitted by design (single-user tool).
 */
@RestController
@RequestMapping("/api/saved-searches")
public class SavedSearchController {

    private final SavedSearchService service;

    public SavedSearchController(SavedSearchService service) {
        this.service = service;
    }

    /** All saved searches, or just those for a module when {@code ?module=} is given. */
    @GetMapping
    public List<SavedSearchDto> list(@RequestParam(required = false) String module) {
        List<SavedSearch> rows = module != null && !module.isBlank()
                ? service.forModule(module) : service.all();
        return rows.stream().map(SavedSearchDto::of).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<SavedSearchDto> get(@PathVariable Long id) {
        SavedSearch s = service.get(id);
        return s == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(SavedSearchDto.of(s));
    }

    @PostMapping
    public SavedSearchDto create(@RequestBody SavedSearchDto dto) {
        SavedSearch s = new SavedSearch();
        dto.applyTo(s);
        return SavedSearchDto.of(service.create(s));
    }

    @PutMapping("/{id}")
    public SavedSearchDto update(@PathVariable Long id, @RequestBody SavedSearchDto dto) {
        SavedSearch in = new SavedSearch();
        dto.applyTo(in);
        return SavedSearchDto.of(service.update(id, in));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Execute a saved search and return rows / affected-count / form-params / error. */
    @PostMapping("/{id}/run")
    public Map<String, Object> run(@PathVariable Long id) {
        return service.run(id);
    }

    /** Execute an unsaved query (preview/test before saving). Body: {queryType, query}. */
    @PostMapping("/run")
    public Map<String, Object> runAdhoc(@RequestBody Map<String, String> body) {
        return service.execute(body.get("queryType"), body.get("query"));
    }
}
