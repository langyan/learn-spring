package com.lin.spring.keycloak.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiController {
    @GetMapping("/public")
    public String publicEndpoint() {
        return "This is a public endpoint.";
    }

    @GetMapping("/secure")
    public String secureEndpoint() {
        return "This is a secure endpoint.";
    }

    @PreAuthorize("hasRole('admin')")
    @GetMapping("/admin")
    public String adminEndpoint() {
        return "This is an admin endpoint.";
    }
}
