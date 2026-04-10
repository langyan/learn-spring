package com.lin.spring.apiprotect.controller;

import com.lin.spring.apiprotect.dto.AdminUserResponse;
import com.lin.spring.apiprotect.service.UserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserResponse>> users(
            @RequestParam(defaultValue = "") String email,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "username") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(userService.searchUsers(email, role, sortBy, direction, page, size));
    }

    @GetMapping("/audit")
    public ResponseEntity<Map<String, Object>> audit() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Admin-only audit endpoint is protected",
                "controls", new String[]{"role-based access", "rate limiting", "masked output"}
        ));
    }
}
