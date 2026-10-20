package com.lin.spring.shiro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT signing configuration (app.jwt.*).
 * HS256 requires a secret of at least 256 bits (32 chars).
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long expirationMs
) {
}
