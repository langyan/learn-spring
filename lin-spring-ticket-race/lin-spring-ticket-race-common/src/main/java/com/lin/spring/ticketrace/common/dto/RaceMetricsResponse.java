package com.lin.spring.ticketrace.common.dto;

public record RaceMetricsResponse(
        double holdSuccessTotal,
        double holdFailureTotal,
        double optimisticConflictTotal,
        double releaseExpiredTotal,
        double confirmSuccessTotal
) {
}
