package com.lin.spring.security.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInMs,
        UserInfoResponse user
) {
}
