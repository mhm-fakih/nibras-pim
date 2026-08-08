package com.nibras.pim.web;

import com.nibras.pim.service.MergeService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Merges two records of the same type into one (the primary survives). */
@RestController
@RequestMapping("/api/{module}/merge")
public class MergeController {

    private final MergeService service;

    public MergeController(MergeService service) {
        this.service = service;
    }

    public record MergeRequest(Long primaryId, Long secondaryId) {
    }

    @PostMapping
    public Object merge(@PathVariable String module, @RequestBody MergeRequest request) {
        return service.merge(module, request.primaryId(), request.secondaryId());
    }
}
