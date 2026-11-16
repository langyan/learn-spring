package com.lin.spring.moviebooking.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * booking-service -> payment-service intent creation command.
 * scenario drives the simulated gateway behaviour:
 * NORMAL | FAIL | LATE | DUPLICATE | LOST (for the demo mainline).
 */
public record PaymentCommand(
        @NotBlank String bookingReference,
        @NotNull Long userId,
        @NotBlank String idempotencyKey,
        String channel,
        @NotNull BigDecimal amount,
        String scenario
) {}
