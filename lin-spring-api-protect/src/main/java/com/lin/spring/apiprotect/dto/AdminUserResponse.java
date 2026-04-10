package com.lin.spring.apiprotect.dto;

public class AdminUserResponse {

    private final String username;
    private final String displayName;
    private final String email;
    private final String role;
    private final boolean locked;

    public AdminUserResponse(String username, String displayName, String email, String role, boolean locked) {
        this.username = username;
        this.displayName = displayName;
        this.email = email;
        this.role = role;
        this.locked = locked;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public boolean isLocked() {
        return locked;
    }
}
