package com.nibras.pim.web;

import com.nibras.pim.service.EntityCatalog;
import com.nibras.pim.service.ReportService;
import com.nibras.pim.web.dto.PageResponse;
import com.nibras.pim.web.dto.ReportDto;
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

/**
 * Saved reports: the definitions, and running one.
 *
 * <p>Writes sit under {@code /api} with POST/PUT/DELETE, so SecurityConfig already gates them on
 * {@code content.write}; running a report is a read, gated like any other list.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;
    private final EntityCatalog catalog;

    public ReportController(ReportService service, EntityCatalog catalog) {
        this.service = service;
        this.catalog = catalog;
    }

    @GetMapping
    public List<ReportDto> list(@RequestParam(required = false) String module) {
        return service.list(module);
    }

    /** Which record types a report can be built over — the picker's module dropdown. */
    @GetMapping("/modules")
    public List<String> modules() {
        return catalog.names();
    }

    @GetMapping("/{id}")
    public ReportDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ReportDto create(@RequestBody ReportDto dto) {
        return service.save(null, dto);
    }

    @PutMapping("/{id}")
    public ReportDto update(@PathVariable Long id, @RequestBody ReportDto dto) {
        return service.save(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Run a saved report. */
    @GetMapping("/{id}/run")
    public PageResponse<Object> run(@PathVariable Long id,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(required = false) Integer size) {
        return service.run(service.get(id), page, size);
    }

    /**
     * Run a definition that hasn't been saved — the builder's live preview.
     *
     * <p>Deliberately the same code path as {@link #run}, rather than the client assembling an
     * equivalent list call: a preview whose rows are produced differently from the real thing is
     * worse than no preview, because it is believed. It also means a query's error message
     * (a typo, {@code sort by} instead of {@code order by}) is seen while typing rather than after
     * saving.
     *
     * <p>A POST because it carries a body, not because it changes anything — it does not.
     */
    @PostMapping("/preview")
    public PageResponse<Object> preview(@RequestBody ReportDto dto,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "5") int size) {
        return service.run(dto, page, size);
    }
}
