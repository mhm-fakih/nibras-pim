package com.nibras.pim.web;

import com.nibras.pim.service.LookupAdminService;
import com.nibras.pim.service.LookupAdminService.LookupGroup;
import com.nibras.pim.service.LookupAdminService.LookupValue;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Manage parametric lookup values (statuses, types, context): add/rename/remove, plus the chip
 * colours (style) and each parameter's default value. */
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

    public record ValueRequest(String name, String code) {
    }

    @PostMapping("/{table}")
    @ResponseStatus(HttpStatus.CREATED)
    public LookupValue create(@PathVariable String table, @RequestBody ValueRequest body) {
        return service.create(table, body.name(), body.code());
    }

    @PutMapping("/{table}/{id}")
    public void rename(@PathVariable String table, @PathVariable long id, @RequestBody ValueRequest body) {
        service.rename(table, id, body.name(), body.code());
    }

    @DeleteMapping("/{table}/{id}")
    public void delete(@PathVariable String table, @PathVariable long id) {
        service.delete(table, id);
    }
}
