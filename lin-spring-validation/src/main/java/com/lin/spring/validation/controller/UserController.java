package com.lin.spring.validation.controller;

import jakarta.validation.Valid;

import com.lin.spring.validation.dto.UserRegistrationRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(
            @Valid @RequestBody UserRegistrationRequest registrationRequest) {
        
        // If we reach here, validation passed
        // Business logic to register the user would go here
        
        return ResponseEntity.ok("User registered successfully: " + registrationRequest.getUsername());
    }
}