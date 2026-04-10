package com.lin.spring.ticketrace.common.dto;

import com.lin.spring.ticketrace.common.enums.LockStrategy;

import java.time.Duration;

public record SimulationResponse(
        String showId,
        String seatCode,
        LockStrategy strategy,
        int concurrency,
        int successCount,
        int failureCount,
        Duration elapsed
) {
}
