package com.lin.spring.security.controller;

import com.lin.spring.security.security.AuthorityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * /api/v1/admin/** is protected at the URL level by SecurityConfig
 * (hasRole("ADMIN")); other endpoints additionally demonstrate
 * fine-grained @PreAuthorize permission checks.
 *
 * <p>The cache endpoint exposes authority-cache eviction, which is how
 * permission changes take effect immediately under the slim-token pattern.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AuthorityResolver authorityResolver;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(Map.of(
                "totalUsers", 2,
                "activeTokens", "stateless (slim JWT + Redis authorities)",
                "generatedAt", Instant.now().toString()));
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('admin:read')")
    public ResponseEntity<Map<String, Object>> audit() {
        return ResponseEntity.ok(Map.of(
                "entries", 0,
                "note", "visible only to authorities containing admin:read"));
    }

    /**
     * Evicts a user's cached authorities (Redis key auth:authorities:{username}).
     * Call this after changing the user's roles/permissions so the change
     * applies to already-issued tokens on their next request.
     */
    @DeleteMapping("/authority-cache/{username}")
    @PreAuthorize("hasAuthority('admin:read')")
    public ResponseEntity<Map<String, String>> evictAuthorityCache(@PathVariable String username) {
        authorityResolver.evict(username);
        return ResponseEntity.ok(Map.of(
                "message", "authority cache evicted for '" + username + "'"));
    }
}
