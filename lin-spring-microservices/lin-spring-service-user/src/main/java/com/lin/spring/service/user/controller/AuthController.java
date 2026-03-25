package com.lin.spring.service.user.controller;

import com.lin.spring.service.user.dto.AuthResponse;
import com.lin.spring.service.user.dto.LoginRequest;
import com.lin.spring.service.user.dto.RegisterRequest;
import com.lin.spring.service.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;

    /**
     * Register new user
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request for: {}", request.getUsername());
        return ResponseEntity.ok(userService.register(request));
    }

    /**
     * Login user
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request for: {}", request.getUsername());
        return ResponseEntity.ok(userService.login(request));
    }
}
