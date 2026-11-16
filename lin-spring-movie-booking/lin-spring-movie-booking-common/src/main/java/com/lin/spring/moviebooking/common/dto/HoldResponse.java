package com.lin.spring.moviebooking.common.dto;

import com.lin.spring.moviebooking.common.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record HoldResponse(
        String reference,
        String holdId,
        Long userId,
        Long showId,
        List<Long> seatIds,
        BigDecimal totalAmount,
        Instant holdExpiresAt,
        BookingStatus status
) {}
