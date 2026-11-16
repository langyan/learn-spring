package com.lin.spring.moviebooking.common.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Concurrency simulation: N users race for the same seat set.
 * oversellDetected must always be 0 — that is the whole point.
 */
public record SimulationRequest(
        @NotNull Long showId,
        @NotEmpty List<Long> seatIds,
        @Min(1) int concurrency,
        /** Stagger between submit and hold attempt, ms. */
        @Min(0) long staggerMs
) {}
