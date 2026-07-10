package com.nibras.pim.web;

import com.nibras.pim.service.AdminStatsService;
import com.nibras.pim.service.AdminStatsService.ModuleStats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Administration dashboard: per-table record counts and parametric-field usage. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminStatsService service;

    public AdminController(AdminStatsService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    public List<ModuleStats> stats() {
        return service.stats();
    }
}
