package com.nibras.pim.web;

import com.nibras.pim.service.HighlightsReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The "All highlights" report: every ==marked== passage in resources, local notes and AI notes. */
@RestController
public class AllHighlightsController {

    private final HighlightsReportService service;

    public AllHighlightsController(HighlightsReportService service) {
        this.service = service;
    }

    @GetMapping("/api/highlights/all")
    public HighlightsReportService.Report all() {
        return service.report();
    }
}
