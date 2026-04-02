package com.dms.dto;

public class LoginResponse {
    private String email;
    private String token;

    public LoginResponse(String email, String token) {
        this.email = email;
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public String getEmail() {
        return email;
    }
}