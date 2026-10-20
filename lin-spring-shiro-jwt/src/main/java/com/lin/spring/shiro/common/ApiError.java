package com.lin.spring.shiro.common;

import java.time.Instant;

/**
 * Uniform error payload returned by all failure paths
 * (401/403 security responses and the global exception handler).
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
