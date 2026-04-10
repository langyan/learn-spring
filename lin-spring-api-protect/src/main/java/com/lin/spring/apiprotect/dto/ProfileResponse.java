package com.lin.spring.apiprotect.dto;

public class ProfileResponse {

    private final String username;
    private final String displayName;
    private final String bio;
    private final String email;
    private final String role;

    public ProfileResponse(String username, String displayName, String bio, String email, String role) {
        this.username = username;
        this.displayName = displayName;
        this.bio = bio;
        this.email = email;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBio() {
        return bio;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }
}
