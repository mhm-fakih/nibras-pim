package com.nibras.pim.web;

import com.nibras.pim.service.AdminStatsService;
import com.nibras.pim.service.ClientSyncViewService;
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

    private final ClientSyncViewService clientSync;

    public AdminController(AdminStatsService service, ClientSyncViewService clientSync) {
        this.clientSync = clientSync;
        this.service = service;
    }

    @GetMapping("/stats")
    public List<ModuleStats> stats() {
        return service.stats();
    }

    /** The parameter vocabulary the mobile/terminal/desktop clients filter to — for spotting drift
     *  between what this server holds and what a device is actually showing. */
    @GetMapping("/client-sync")
    public ClientSyncViewService.ClientSyncView clientSync() {
        return clientSync.view();
    }
}
