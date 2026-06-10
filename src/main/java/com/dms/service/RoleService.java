package com.dms.service;

import com.dms.dao.RoleRepository;
import com.dms.models.Role;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RoleService {

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
        role.setPermissions(permissions);

        return roleRepository.save(role);
    }

    //  Update role permissions
    public Role updateRole(UUID roleId, String permissions) {

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        role.setPermissions(permissions);

        return roleRepository.save(role);
    }

    // Delete role
    public void deleteRole(UUID roleId) {
        if (!roleRepository.existsById(roleId)) {
            throw new RuntimeException("Role not found");
        }
        roleRepository.deleteById(roleId);
    }
}