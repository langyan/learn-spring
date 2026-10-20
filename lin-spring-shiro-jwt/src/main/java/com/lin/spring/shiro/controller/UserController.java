package com.lin.spring.shiro.controller;

import com.lin.spring.shiro.dto.UserInfoResponse;
import com.lin.spring.shiro.service.UserService;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Method-level authorization uses Shiro's @RequiresPermissions — the
 * counterpart of @PreAuthorize("hasAuthority('...')") in the Spring
 * Security edition. Permission strings use Shiro wildcard syntax
 * (domain:action), which matches our RBAC seed data as-is.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> me() {
        String username = (String) SecurityUtils.getSubject().getPrincipal();
        return ResponseEntity.ok(userService.getCurrentUser(username));
    }

    @RequiresPermissions("user:read")
    @GetMapping
    public ResponseEntity<List<UserInfoResponse>> listUsers() {
        return ResponseEntity.ok(userService.listUsers());
    }

    @RequiresPermissions("user:write")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
