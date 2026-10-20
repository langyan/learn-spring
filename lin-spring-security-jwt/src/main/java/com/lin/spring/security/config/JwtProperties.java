package com.lin.spring.security.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT configuration bound from "app.jwt.*" properties.
 *
 * @param secret       signing key for HS256, must be at least 32 characters (256 bits)
 * @param expirationMs access token time-to-live in milliseconds
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "HS256 secret must be at least 32 characters") String secret,
        @Positive long expirationMs
) {
}
