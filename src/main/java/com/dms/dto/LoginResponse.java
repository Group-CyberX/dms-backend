package com.dms.dto;

import java.util.Map;

public class LoginResponse {

    private final String email;
    private final String token;
    private final String role;
    private final Map<String, Boolean> permissions;

    public LoginResponse(String email, String token, String role, Map<String, Boolean> permissions) {
        this.email = email;
        this.token = token;
        this.role = role;
        this.permissions = permissions;
    }

    public String getEmail() {
        return email;
    }

    public String getToken() {
        return token;
    }

    public String getRole() {
        return role;
    }

    public Map<String, Boolean> getPermissions() {
        return permissions;
    }
}