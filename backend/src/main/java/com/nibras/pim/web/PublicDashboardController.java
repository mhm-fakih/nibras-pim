package com.nibras.pim.web;

import com.nibras.pim.service.PublicDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unauthenticated read of one dashboard's fully-resolved snapshot — backs the chrome-free "view"
 * page meant to be left open on a TV or spare monitor with no login. See
 * {@link PublicDashboardService} for why this is one narrow aggregate endpoint rather than opening
 * the generic content read endpoints to anonymous callers. Permitted anonymously in SecurityConfig.
 */
@RestController
@RequestMapping("/api/public/custom-dashboards")
public class PublicDashboardController {

    private final PublicDashboardService service;

    public PublicDashboardController(PublicDashboardService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public PublicDashboardService.Snapshot snapshot(@PathVariable Long id) {
        return service.snapshot(id);
    }
}
