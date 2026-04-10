package com.lin.spring.apiprotect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProfileUpdateRequest {

    @NotBlank(message = "displayName is required")
    @Size(max = 80, message = "displayName must be at most 80 characters")
    private String displayName;

    @Size(max = 500, message = "bio must be at most 500 characters")
    private String bio;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}
