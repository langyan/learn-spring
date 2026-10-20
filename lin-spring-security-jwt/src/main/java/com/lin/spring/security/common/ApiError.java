package com.lin.spring.security.common;

import java.time.Instant;

/**
 * Uniform error payload returned by all failure paths
 * (401/403 security handlers and the global exception handler).
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
