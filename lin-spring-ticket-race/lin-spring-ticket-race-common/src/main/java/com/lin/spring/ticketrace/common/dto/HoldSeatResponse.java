package com.lin.spring.ticketrace.common.dto;

import com.lin.spring.ticketrace.common.enums.BookingStatus;
import com.lin.spring.ticketrace.common.enums.LockStrategy;

import java.time.Instant;

public record HoldSeatResponse(
        String bookingNo,
        String holdToken,
        String userId,
        String showId,
        String seatCode,
        LockStrategy strategy,
        BookingStatus status,
        Instant expiresAt
) {
}
