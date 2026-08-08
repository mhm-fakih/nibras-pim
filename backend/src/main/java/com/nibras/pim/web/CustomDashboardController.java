package com.nibras.pim.web;

import com.nibras.pim.service.CustomDashboardService;
import com.nibras.pim.web.dto.CustomDashboardDto;
import com.nibras.pim.web.dto.DashboardFileContentDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** User-defined dashboards (see {@link CustomDashboardService}), plus a safe file-content read for
 * the "file" component type. */
@RestController
@RequestMapping("/api/custom-dashboards")
public class CustomDashboardController {

    private final CustomDashboardService service;

    public CustomDashboardController(CustomDashboardService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomDashboardDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public CustomDashboardDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomDashboardDto create(@RequestBody CustomDashboardDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public CustomDashboardDto update(@PathVariable Long id, @RequestBody CustomDashboardDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    /**
     * Plain-text content of a file under one of the three repo roots, for the "file" component —
     * e.g. {@code repo=1, path=dashboard-top.md} reads {@code <repo1>/dashboard-top.md}.
     */
    @GetMapping("/file-content")
    public DashboardFileContentDto fileContent(@RequestParam(defaultValue = "1") int repo, @RequestParam String path) {
        return service.readFile(repo, path);
    }
}
