package com.nibras.pim.web;

import com.nibras.pim.service.LookupAdminService;
import com.nibras.pim.service.LookupAdminService.LookupGroup;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Edit the chip colours (style) of parametric lookup values + bulk-fill the empty ones. */
@RestController
@RequestMapping("/api/lookups")
public class LookupAdminController {

    private final LookupAdminService service;

    public LookupAdminController(LookupAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<LookupGroup> list() {
        return service.list();
    }

    public record StyleRequest(String style) {
    }

    @PutMapping("/{table}/{id}/style")
    public void setStyle(@PathVariable String table, @PathVariable long id,
                         @RequestBody StyleRequest body) {
        service.setStyle(table, id, body.style());
    }

    public record DefaultRequest(Long id) {
    }

    /** Set (id) or clear (null) the parameter's default value (applied when a record leaves it blank). */
    @PutMapping("/{table}/default")
    public void setDefault(@PathVariable String table, @RequestBody DefaultRequest body) {
        service.setDefault(table, body.id());
    }

    @PostMapping("/fill-colors")
    public Map<String, Integer> fillColors() {
        return Map.of("filled", service.fillColors());
    }
}
