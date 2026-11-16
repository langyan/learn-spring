package com.lin.spring.moviebooking.common.dto;

/**
 * Race/oversell dashboard. oversellDetected must stay 0 forever;
 * seatsLostAfterCapture measures late-webhook grace-window effectiveness.
 */
public record RaceSummaryResponse(
        long holdsRequested,
        long holdsSucceeded,
        long seatConflicts,
        long oversellDetected,
        long bookingsConfirmed,
        long lateWebhookRecovered,
        long lateWebhookRefunded,
        long seatsLostAfterCapture,
        long expiredHoldsReleased,
        long redisAdmissionDenied
) {}
