package com.lin.spring.ticketrace.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record PaymentCommand(
        @NotBlank String bookingNo,
        @PositiveOrZero long delayMillis,
        boolean forceFailure,
        boolean forceTimeout
) {
}
