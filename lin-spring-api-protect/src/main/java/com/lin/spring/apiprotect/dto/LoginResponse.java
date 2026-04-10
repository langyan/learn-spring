package com.lin.spring.apiprotect.dto;

import java.time.Instant;

public class LoginResponse {

    private final String token;
    private final String tokenType;
    private final String username;
    private final String role;
    private final Instant expiresAt;

    public LoginResponse(String token, String tokenType, String username, String role, Instant expiresAt) {
        this.token = token;
        this.tokenType = tokenType;
        this.username = username;
        this.role = role;
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
