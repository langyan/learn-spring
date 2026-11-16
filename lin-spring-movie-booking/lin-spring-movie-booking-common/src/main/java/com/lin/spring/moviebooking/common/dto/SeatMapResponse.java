package com.lin.spring.moviebooking.common.dto;

import java.time.Instant;
import java.util.List;

/**
 * Seat map snapshot. May be served from a 3-second Redis cache.
 *
 * <p>IMPORTANT: this picture is advisory, never authoritative. The client renders
 * from it, but the actual booking decision always runs on MySQL via the
 * conditional UPDATE in booking-service.</p>
 */
public record SeatMapResponse(
        Long showId,
        Instant generatedAt,
        boolean staleSnapshot,
        int totalSeats,
        int availableSeats,
        List<SeatView> seats
) {}
