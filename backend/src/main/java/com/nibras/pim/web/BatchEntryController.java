package com.nibras.pim.web;

import com.nibras.pim.service.BatchEntryService;
import com.nibras.pim.service.BatchEntryService.EntryRequest;
import com.nibras.pim.service.BatchEntryService.EntryResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Batch-add records: one record per line, parsed with the shared command grammar. */
@RestController
@RequestMapping("/api/batch-entry")
public class BatchEntryController {

    private final BatchEntryService service;

    public BatchEntryController(BatchEntryService service) {
        this.service = service;
    }

    @PostMapping
    public EntryResult add(@RequestBody EntryRequest req) {
        return service.add(req);
    }
}
