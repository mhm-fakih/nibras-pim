package com.nibras.pim.web;

import com.nibras.pim.service.BatchEditService;
import com.nibras.pim.service.BatchEditService.BatchRequest;
import com.nibras.pim.service.BatchEditService.BatchResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bulk edits over selected records via the compact command syntax. */
@RestController
@RequestMapping("/api/{module}/batch")
public class BatchController {

    private final BatchEditService service;

    public BatchController(BatchEditService service) {
        this.service = service;
    }

    @PostMapping
    public BatchResult apply(@PathVariable String module, @RequestBody BatchRequest request) {
        return service.apply(module, request);
    }
}
