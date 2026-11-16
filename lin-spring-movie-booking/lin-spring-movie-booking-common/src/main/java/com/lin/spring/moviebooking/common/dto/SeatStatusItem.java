package com.lin.spring.moviebooking.common.dto;

import com.lin.spring.moviebooking.common.enums.SeatStatus;

import java.math.BigDecimal;

/**
 * Authoritative per-seat status+price row, served by booking-service's
 * internal endpoint when movie-service assembles a seat map snapshot.
 */
public record SeatStatusItem(
        Long seatId,
        SeatStatus status,
        BigDecimal price
) {}
