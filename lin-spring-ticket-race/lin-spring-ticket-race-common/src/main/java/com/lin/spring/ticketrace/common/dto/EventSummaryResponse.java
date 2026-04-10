package com.lin.spring.ticketrace.common.dto;

import java.time.Instant;

public record EventSummaryResponse(
        String eventId,
        String name,
        String venue,
        Instant showTime
) {
}
