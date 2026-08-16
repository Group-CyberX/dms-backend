package com.dms.rest;

import com.dms.dto.DashboardSummary;
import com.dms.security.SecurityUtils;
import com.dms.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * One request, one response, every figure on the dashboard. Scoped to the
     * caller: the SLA list and the "my documents" counts are theirs, not the
     * system's.
     */
    @GetMapping("/summary")
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewDashboard')")
    public DashboardSummary summary() {
        return dashboardService.summarise(SecurityUtils.currentUser());
    }
}
