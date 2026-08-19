package com.dms.dto;

import java.util.Map;

public class LoginResponse {

    private final String email;
    private final String username;
    private final String accessToken;
    private final String refreshToken;
    private final String role;
    private final Map<String, Boolean> permissions;

    /**
     * True when the password was accepted but a code has been emailed and the
     * session has not been issued yet. Every token field is null in that case,
     * so a client that ignores this flag cannot accidentally treat it as a
     * completed sign-in.
     */
    private final boolean twoFactorRequired;

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
        this.twoFactorRequired = false;
    }

    private LoginResponse(String email, boolean twoFactorRequired) {
        this.email = email;
        this.username = null;
        this.accessToken = null;
        this.refreshToken = null;
        this.role = null;
        this.permissions = null;
        this.twoFactorRequired = twoFactorRequired;
    }

    /** Password accepted, code sent - no session until the code is confirmed. */
    public static LoginResponse pendingTwoFactor(String email) {
        return new LoginResponse(email, true);
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

    public boolean isTwoFactorRequired() {
        return twoFactorRequired;
    }
}