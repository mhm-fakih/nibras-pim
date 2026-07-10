package com.nibras.pim.web;

import com.nibras.pim.service.RelationService;
import com.nibras.pim.service.RelationService.RelatedGroup;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Records related to a given record through the legacy foreign keys (Book→excerpts, Goal→tasks, …). */
@RestController
@RequestMapping("/api/related")
public class RelationController {

    private final RelationService service;

    public RelationController(RelationService service) {
        this.service = service;
    }

    @GetMapping("/{module}/{id}")
    public List<RelatedGroup> related(@PathVariable String module, @PathVariable Long id) {
        return service.related(module, id);
    }
}
