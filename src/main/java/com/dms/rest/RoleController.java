package com.dms.rest;

import com.dms.dto.CreateRoleRequest;
import com.dms.models.Role;
import com.dms.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    // Get all roles
    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public List<Role> getAllRoles() {
        return roleService.getAllRoles();
    }

    // Create role
    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public Role createRole(@Valid @RequestBody CreateRoleRequest request) {
        return roleService.createRole(
                request.getName(),
                request.getPermissions()
        );
    }

    // Update role permissions
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public Role updateRole(@PathVariable UUID id,
                           @RequestBody CreateRoleRequest request) {
        return roleService.updateRole(
                id,
                request.getPermissions()
        );
    }
}