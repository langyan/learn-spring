package com.lin.spring.moviebooking.common.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Static seat layout for a show (physical seats + pricing), consumed by
 * booking-service when publishing show inventory (bulk-creating show_seats).
 */
public record SeatLayoutResponse(
        Long showId,
        Long auditoriumId,
        BigDecimal basePrice,
        List<SeatLayoutItem> seats
) {}
