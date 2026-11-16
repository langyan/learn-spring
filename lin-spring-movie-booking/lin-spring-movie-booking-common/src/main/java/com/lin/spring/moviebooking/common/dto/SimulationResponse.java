package com.lin.spring.moviebooking.common.dto;

import java.util.List;

public record SimulationResponse(
        Long showId,
        List<Long> seatIds,
        int concurrency,
        int succeeded,
        int conflicts,
        int failed,
        long oversellDetected,
        long durationMs
) {}
