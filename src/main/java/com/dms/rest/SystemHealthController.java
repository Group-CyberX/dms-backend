package com.dms.rest;

import com.dms.dto.SystemHealthResponse;
import com.dms.service.SystemHealthService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system-health")
public class SystemHealthController {

    private final SystemHealthService systemHealthService;

    public SystemHealthController(SystemHealthService systemHealthService) {
        this.systemHealthService = systemHealthService;
    }

    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, 'canViewHealthSystem')")
    public SystemHealthResponse check() {
        return systemHealthService.check();
    }
}
