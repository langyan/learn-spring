package com.lin.spring.orders.order.controller;

import com.lin.spring.orders.common.security.JwtVerifier;
import com.lin.spring.orders.order.dto.TokenRequest;
import com.lin.spring.orders.order.dto.TokenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模拟登录：演示用，任意 (customerId, name) 即可换取令牌。
 * 真实系统应校验凭据并对接 OAuth2/OIDC。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtVerifier jwtVerifier;

    @Value("${app.jwt.ttl-seconds}")
    private long ttlSeconds;

    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest request) {
        String token = jwtVerifier.issue(request.customerId(), request.name(), ttlSeconds);
        return new TokenResponse(token, "Bearer", ttlSeconds);
    }
}
