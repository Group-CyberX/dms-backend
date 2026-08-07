package com.dms.dto;

import java.util.UUID;

// This is mainly used for dropdowns when selecting approvers
public class ApproverOptionDTO {
    private UUID userId;
    private String username;
    private String role;

    public ApproverOptionDTO() {
    }

    public ApproverOptionDTO(UUID userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}