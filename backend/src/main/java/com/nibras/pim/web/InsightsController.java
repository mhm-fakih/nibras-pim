package com.nibras.pim.web;

import com.nibras.pim.service.InsightsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Backs the Insights page: per-module creation/update activity for histograms + pie charts. */
@RestController
@RequestMapping("/api/insights")
public class InsightsController {

    /** Default covered period — kept short (3 months) so the page motivates reviewing recent work
     *  rather than surfacing years of accumulated records. */
    private static final int DEFAULT_WINDOW_DAYS = 90;

    /** Default "review window" — how far back counts as "reviewed recently". */
    private static final int DEFAULT_REVIEW_DAYS = 7;

    private final InsightsService insights;

    public InsightsController(InsightsService insights) {
        this.insights = insights;
    }

    @GetMapping("/summary")
    public InsightsService.InsightsSummary summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer reviewDays) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_WINDOW_DAYS - 1);
        int review = reviewDays != null ? reviewDays : DEFAULT_REVIEW_DAYS;
        return insights.summary(start, end, review);
    }
}
