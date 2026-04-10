package com.lin.spring.ticketrace.common.dto;

import com.lin.spring.ticketrace.common.enums.PaymentStatus;

import java.time.Instant;

public record PaymentResult(
        String bookingNo,
        PaymentStatus status,
        String message,
        Instant processedAt
) {
}
