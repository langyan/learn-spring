package com.lin.spring.ticketrace.common.dto;

import com.lin.spring.ticketrace.common.enums.BookingStatus;
import com.lin.spring.ticketrace.common.enums.LockStrategy;

import java.time.Instant;

public record BookingResponse(
        String bookingNo,
        String userId,
        String showId,
        String seatCode,
        LockStrategy strategy,
        BookingStatus status,
        Instant expiresAt,
        Instant createdAt,
        String lastFailureReason
) {
}
