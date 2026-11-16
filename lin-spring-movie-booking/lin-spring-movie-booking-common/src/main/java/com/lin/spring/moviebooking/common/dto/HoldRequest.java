package com.lin.spring.moviebooking.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Hold request. Between 1 and 10 seats per reservation (product rule).
 * The idempotency key guards against double-tapped Reserve buttons.
 */
public record HoldRequest(
        @NotNull Long showId,
        @NotEmpty @Size(max = 10) List<Long> seatIds,
        @NotNull Long userId,
        @NotBlank String idempotencyKey
) {}
