package com.dms.service;

import com.dms.dao.RoleRepository;
import com.dms.models.Role;
import com.dms.security.PermissionCatalog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RoleService {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    //  Get all roles
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    //  Create role
    public Role createRole(String name, String permissions) {

        if (roleRepository.findByName(name).isPresent()) {
            throw new RuntimeException("Role already exists");
        }

        Role role = new Role();
        role.setRoleId(UUID.randomUUID());
        role.setName(name.toUpperCase());
        role.setPermissions(canonicalizePermissions(permissions));

        return roleRepository.save(role);
    }

    //  Update role permissions
    public Role updateRole(UUID roleId, String permissions) {

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        role.setPermissions(canonicalizePermissions(permissions));

        return roleRepository.save(role);
    }

    /**
     * Rejects unknown permission keys and rewrites the map so it contains every
     * catalogue key and nothing else. Keys left over from earlier naming
     * schemes are dropped instead of quietly keeping access.
     */
    private String canonicalizePermissions(String permissions) {

        if (permissions == null || permissions.isBlank()) {
            throw new RuntimeException("Permissions are required");
        }

        Map<String, Object> submitted;

        try {
            submitted = objectMapper.readValue(
                    permissions,
                    new TypeReference<Map<String, Object>>() {}
            );
        } catch (Exception e) {
            throw new RuntimeException("Permissions must be a valid JSON object");
        }

        List<String> unknownKeys = submitted.keySet().stream()
                .filter(key -> !PermissionCatalog.isKnown(key))
                .sorted()
                .toList();

        if (!unknownKeys.isEmpty()) {
            throw new RuntimeException("Unknown permission keys: " + String.join(", ", unknownKeys));
        }

        Map<String, Boolean> canonical = new LinkedHashMap<>();

        for (String key : PermissionCatalog.keys()) {
            canonical.put(key, isEnabled(submitted.get(key)));
        }

        try {
            return objectMapper.writeValueAsString(canonical);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store permissions");
        }
    }

    private boolean isEnabled(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        return value != null && Boolean.parseBoolean(value.toString());
    }

    // Delete role
    public void deleteRole(UUID roleId) {
        if (!roleRepository.existsById(roleId)) {
            throw new RuntimeException("Role not found");
        }
        roleRepository.deleteById(roleId);
    }
}