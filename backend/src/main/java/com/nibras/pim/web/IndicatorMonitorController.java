package com.nibras.pim.web;

import com.nibras.pim.service.IndicatorMonitorService;
import com.nibras.pim.service.IndicatorMonitorService.DueItem;
import com.nibras.pim.service.IndicatorMonitorService.Proposal;
import com.nibras.pim.service.IndicatorMonitorService.SeriesPoint;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/** Indicator monitoring workflow: due reminders, proposed values, confirm-save, time series. */
@RestController
@RequestMapping("/api/indicators")
public class IndicatorMonitorController {

    private final IndicatorMonitorService service;

    public IndicatorMonitorController(IndicatorMonitorService service) {
        this.service = service;
    }

    public record DataRequest(Double value, LocalDateTime date, String notes) {
    }

    /** Bookmarked indicators missing the current period's data, with proposed values. */
    @GetMapping("/due")
    public List<DueItem> due() {
        return service.due();
    }

    /** Recompute the proposed value for one indicator (not saved). */
    @GetMapping("/{id}/propose")
    public Proposal propose(@PathVariable Long id) {
        return service.propose(id);
    }

    /** Confirm a value: append a data point. */
    @PostMapping("/{id}/data")
    public ResponseEntity<Void> addData(@PathVariable Long id, @RequestBody DataRequest req) {
        service.saveData(id, req.value(), req.date(), req.notes());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/series")
    public List<SeriesPoint> series(@PathVariable Long id) {
        return service.series(id);
    }
}
