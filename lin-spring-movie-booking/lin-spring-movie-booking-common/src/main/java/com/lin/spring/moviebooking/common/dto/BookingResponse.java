package com.lin.spring.moviebooking.common.dto;

import com.lin.spring.moviebooking.common.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingResponse(
        String reference,
        Long userId,
        Long showId,
        BookingStatus status,
        List<Long> seatIds,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant holdExpiresAt,
        Instant confirmedAt
) {}
