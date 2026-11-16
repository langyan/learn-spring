package com.lin.spring.moviebooking.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payment initiation. Entering the payment screen also extends the hold window
 * once (5 min -> 10 min in the reference configuration).
 *
 * <p>scenario steers the SIMULATED gateway for demos:
 * NORMAL | FAIL | LATE | DUPLICATE | LOST (defaults to NORMAL).</p>
 */
public record PaymentInitiationRequest(
        @NotBlank String bookingReference,
        @NotNull Long userId,
        @NotBlank String idempotencyKey,
        /** UPI or CARD — drives simulated gateway latency in payment-service. */
        String channel,
        String scenario
) {}
