package com.lin.spring.moviebooking.common.dto;

import com.lin.spring.moviebooking.common.enums.SeatStatus;

import java.math.BigDecimal;

/**
 * Single seat cell in a seat map snapshot.
 */
public record SeatView(
        Long seatId,
        String rowLabel,
        int seatNumber,
        SeatStatus status,
        BigDecimal price
) {}
