package com.dms.dto;

import java.util.Map;

public class LoginResponse {

    private final String email;
    private final String username;
    private final String accessToken;
    private final String refreshToken;
    private final String role;
    private final Map<String, Boolean> permissions;

    public LoginResponse(String email,
                         String username,
                         String accessToken,
                         String refreshToken,
                         String role,
                         Map<String, Boolean> permissions) {

        this.email = email;
        this.username = username;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.role = role;
        this.permissions = permissions;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getRole() {
        return role;
    }

    public Map<String, Boolean> getPermissions() {
        return permissions;
    }
}