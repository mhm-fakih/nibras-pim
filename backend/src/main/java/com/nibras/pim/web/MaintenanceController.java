package com.nibras.pim.web;

import com.nibras.pim.service.MaintenanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The Maintenance page's two reports and their two actions.
 *
 * <p>The reports are GETs (a scan, however slow, changes nothing); the actions move files and clear
 * columns, so they are POSTs and inherit the {@code content.write} gate SecurityConfig puts on
 * every write under {@code /api}.
 */
@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private static final int DEFAULT_TOP = 30;

    private final MaintenanceService service;

    public MaintenanceController(MaintenanceService service) {
        this.service = service;
    }

    /** Heaviest repo-1 record folders. */
    @GetMapping("/disk")
    public MaintenanceService.UsageReport disk(@RequestParam(defaultValue = "" + DEFAULT_TOP) int limit) {
        return service.folderUsage(limit);
    }

    /** Resources carrying the most {@code fullText}. */
    @GetMapping("/fulltext")
    public MaintenanceService.FulltextReport fulltext(@RequestParam(defaultValue = "" + DEFAULT_TOP) int limit) {
        return service.longestFullText(limit);
    }

    public record MoveRequest(List<MaintenanceService.Target> records) {
    }

    @PostMapping("/move-to-repo2")
    public MaintenanceService.MoveResult move(@RequestBody MoveRequest request) {
        return service.moveToRepo2(request.records());
    }

    public record OffloadRequest(List<Long> ids) {
    }

    @PostMapping("/offload-fulltext")
    public MaintenanceService.OffloadResult offload(@RequestBody OffloadRequest request) {
        return service.offloadFullText(request.ids());
    }
}
