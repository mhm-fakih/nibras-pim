package com.nibras.pim.web;

import com.nibras.pim.service.DashboardService;
import com.nibras.pim.service.DashboardService.BookmarkItem;
import com.nibras.pim.service.DashboardService.Kpi;
import com.nibras.pim.web.dto.KanbanDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    /** Bookmarked goals + their tasks as full DTOs (grouped client-side by department/goal). */
    @GetMapping("/kanban")
    public KanbanDto kanban() {
        return service.kanban();
    }

    /** Every bookmarked record across all modules, ordered by module. */
    @GetMapping("/bookmarks")
    public List<BookmarkItem> bookmarks() {
        return service.bookmarks();
    }

    /** Standard at-a-glance KPIs for the Indicators page. */
    @GetMapping("/kpis")
    public List<Kpi> kpis() {
        return service.kpis();
    }
}
