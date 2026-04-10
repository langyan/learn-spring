package com.lin.spring.ticketrace.common.dto;

import com.lin.spring.ticketrace.common.enums.LockStrategy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record HoldSeatRequest(
        @NotBlank String userId,
        @NotBlank String showId,
        @NotBlank String seatCode,
        @NotNull LockStrategy strategy,
        @Positive long holdSeconds
) {
}
