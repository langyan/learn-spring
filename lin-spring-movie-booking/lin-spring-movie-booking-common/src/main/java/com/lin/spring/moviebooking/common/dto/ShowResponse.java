package com.lin.spring.moviebooking.common.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Catalog-level show info. Static by design: availability counts are NOT here —
 * they live in the seat map snapshot (GET /api/v1/shows/{showId}/seats),
 * which is allowed to be a few seconds stale.
 */
public record ShowResponse(
        Long id,
        Long movieId,
        String movieTitle,
        String city,
        Long auditoriumId,
        String auditorium,
        LocalDateTime startsAt,
        BigDecimal basePrice
) {}
