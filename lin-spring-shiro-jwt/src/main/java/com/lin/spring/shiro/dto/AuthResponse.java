package com.lin.spring.shiro.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInMs,
        UserInfoResponse user
) {
}
