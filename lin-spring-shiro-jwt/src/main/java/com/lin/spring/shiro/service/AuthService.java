package com.lin.spring.shiro.service;

import com.lin.spring.shiro.auth.AuthorityResolver;
import com.lin.spring.shiro.auth.JwtUtil;
import com.lin.spring.shiro.auth.UserAuthService;
import com.lin.spring.shiro.dto.AuthResponse;
import com.lin.spring.shiro.dto.LoginRequest;
import com.lin.spring.shiro.dto.UserInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Login flow (slim-token variant): UserAuthService verifies credentials
 * (BCrypt against the database — no Shiro login subject is created, the
 * issued JWT IS the credential for later requests), then JwtUtil issues an
 * identity-only token (~150 bytes, no permission claims). The authority
 * cache is evicted on login so the fresh permission set from the database
 * is loaded on the next request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAuthService userAuthService;
    private final JwtUtil jwtUtil;
    private final AuthorityResolver authorityResolver;

    public AuthResponse login(LoginRequest request) {
        String username = userAuthService.authenticate(request.username(), request.password())
                .getUsername();

        String accessToken = jwtUtil.generateToken(username);
        UserInfoResponse userInfo = userAuthService.getUserInfo(username);

        // drop any cached (possibly stale) authorities from a previous session
        authorityResolver.evict(username);

        log.info("User '{}' logged in successfully", username);
        return new AuthResponse(accessToken, "Bearer", jwtUtil.getExpirationMs(), userInfo);
    }
}
