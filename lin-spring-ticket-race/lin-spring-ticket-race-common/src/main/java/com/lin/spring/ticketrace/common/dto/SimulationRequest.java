package com.lin.spring.ticketrace.common.dto;

import com.lin.spring.ticketrace.common.enums.LockStrategy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SimulationRequest(
        @NotBlank String showId,
        @NotBlank String seatCode,
        @Min(1) int concurrency,
        @NotNull LockStrategy strategy,
        @Min(1) long holdSeconds
) {
}
