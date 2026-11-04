package com.nibras.pim.web;

import com.nibras.pim.service.catalog.CatalogService;
import com.nibras.pim.service.catalog.CatalogService.CatalogFile;
import com.nibras.pim.service.catalog.CatalogService.Location;
import com.nibras.pim.service.catalog.CatalogService.LocationInput;
import com.nibras.pim.service.catalog.CatalogService.RecordSummary;
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
 * The disk catalogue (see {@link CatalogService}): storage locations, their scans, and the record
 * files found on them. Writes (POST/PUT/DELETE) fall under SecurityConfig's content-write rule.
 */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/locations")
    public List<Location> locations() {
        return catalog.locations();
    }

    @PostMapping("/locations")
    public Location create(@RequestBody LocationInput in) {
        return catalog.create(in);
    }

    @PutMapping("/locations/{id}")
    public Location update(@PathVariable long id, @RequestBody LocationInput in) {
        return catalog.update(id, in);
    }

    @DeleteMapping("/locations/{id}")
    public void delete(@PathVariable long id) {
        catalog.delete(id);
    }

    /** Start a scan; poll {@link #locations()} for progress. */
    @PostMapping("/locations/{id}/scan")
    public Location scan(@PathVariable long id) {
        return catalog.scan(id);
    }

    @GetMapping("/locations/{id}/records")
    public List<RecordSummary> records(@PathVariable long id, @RequestParam(defaultValue = "1000") int limit) {
        return catalog.recordsOn(id, limit);
    }

    @GetMapping("/search")
    public List<CatalogFile> search(@RequestParam(defaultValue = "") String q,
                                    @RequestParam(required = false) Long location,
                                    @RequestParam(defaultValue = "300") int limit) {
        return catalog.search(q, location, limit);
    }

    @GetMapping("/records/{module}/{id}")
    public List<CatalogFile> recordFiles(@PathVariable String module, @PathVariable long id) {
        return catalog.recordFiles(module, id);
    }

    @GetMapping("/records/{module}/{id}/summary")
    public List<RecordSummary> recordSummary(@PathVariable String module, @PathVariable long id) {
        return catalog.recordSummary(module, id);
    }
}
