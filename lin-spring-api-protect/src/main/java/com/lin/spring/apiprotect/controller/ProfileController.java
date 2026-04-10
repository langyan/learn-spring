package com.lin.spring.apiprotect.controller;

import com.lin.spring.apiprotect.dto.ProfileResponse;
import com.lin.spring.apiprotect.dto.ProfileUpdateRequest;
import com.lin.spring.apiprotect.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> me(Authentication authentication) {
        return ResponseEntity.ok(userService.getCurrentProfile(authentication.getName()));
    }

    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> profile(Authentication authentication) {
        return ResponseEntity.ok(userService.getCurrentProfile(authentication.getName()));
    }

    @PutMapping("/profile")
    public ResponseEntity<ProfileResponse> updateProfile(Authentication authentication,
                                                         @Valid @RequestBody ProfileUpdateRequest request) {
        return ResponseEntity.ok(userService.updateProfile(authentication.getName(), request));
    }
}
