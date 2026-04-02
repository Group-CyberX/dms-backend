package com.dms.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @Column(name = "role_id")
    private UUID roleId;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "permissions", nullable = false, columnDefinition = "jsonb")
    private String permissions;

    public Role() {
    }

    public UUID getRoleId() {
        return this.roleId;
    }

    public void setRoleId(UUID roleId) {
        this.roleId = roleId;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPermissions() {
        return this.permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }
}