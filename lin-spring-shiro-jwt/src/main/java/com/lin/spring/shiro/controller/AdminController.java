package com.lin.spring.shiro.controller;

import com.lin.spring.shiro.auth.AuthorityResolver;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Class-level @RequiresRoles("ADMIN") mirrors the Spring Security edition's
 * path rule requestMatchers("/api/v1/admin/**").hasRole("ADMIN") — here it
 * is enforced by annotation AOP instead of the filter chain, so a 403 flows
 * through GlobalExceptionHandler as uniform JSON. Note Shiro role checks
 * are case-sensitive: the realm stores roles WITHOUT the ROLE_ prefix.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@RequiresRoles("ADMIN")
public class AdminController {

    private final AuthorityResolver authorityResolver;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(Map.of(
                "totalUsers", 2,
                "activeTokens", "stateless (slim JWT + Redis authorities)",
                "generatedAt", Instant.now().toString()));
    }

    @RequiresPermissions("admin:read")
    @GetMapping("/audit")
    public ResponseEntity<Map<String, Object>> audit() {
        return ResponseEntity.ok(Map.of(
                "entries", 0,
                "note", "visible only to permissions containing admin:read"));
    }

    /**
     * Evicts a user's cached authorities (Redis key auth:authorities:{username}).
     * Call this after changing the user's roles/permissions so the change
     * applies to already-issued tokens on their next request.
     */
    @RequiresPermissions("admin:read")
    @DeleteMapping("/authority-cache/{username}")
    public ResponseEntity<Map<String, String>> evictAuthorityCache(@PathVariable String username) {
        authorityResolver.evict(username);
        return ResponseEntity.ok(Map.of(
                "message", "authority cache evicted for '" + username + "'"));
    }
}
