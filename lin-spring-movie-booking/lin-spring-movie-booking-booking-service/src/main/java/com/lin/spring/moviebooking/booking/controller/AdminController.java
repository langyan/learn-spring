package com.lin.spring.moviebooking.booking.controller;

import com.lin.spring.moviebooking.booking.repository.OutboxEventRepository;
import com.lin.spring.moviebooking.booking.service.HoldExpiryScheduler;
import com.lin.spring.moviebooking.booking.service.MovieBookingMetrics;
import com.lin.spring.moviebooking.common.dto.RaceSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Operational dashboard: race summary (oversell must be 0), manual sweeps
 * and outbox dead-letter replay.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final MovieBookingMetrics metrics;
    private final HoldExpiryScheduler holdExpiryScheduler;
    private final OutboxEventRepository outboxRepository;

    @GetMapping("/metrics/race-summary")
    public RaceSummaryResponse raceSummary() {
        return new RaceSummaryResponse(
                metrics.holdsRequested(),
                metrics.holdsSucceeded(),
                metrics.seatConflicts(),
                metrics.oversellDetected(),
                metrics.bookingsConfirmed(),
                metrics.lateWebhookRecovered(),
                metrics.lateWebhookRefunded(),
                metrics.seatsLostAfterCapture(),
                metrics.expiredHoldsReleased(),
                metrics.redisAdmissionDenied());
    }

    @PostMapping("/expiry/sweep")
    public String sweepNow() {
        holdExpiryScheduler.sweepExpiredHolds();
        return "sweep triggered";
    }

    @GetMapping("/outbox/stats")
    public Map<String, Object> outboxStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("pending", outboxRepository.countByStatus("PENDING"));
        stats.put("processing", outboxRepository.countByStatus("PROCESSING"));
        stats.put("published", outboxRepository.countByStatus("PUBLISHED"));
        stats.put("failed", outboxRepository.countByStatus("FAILED"));
        return stats;
    }

    /** Replay dead-lettered outbox rows (FAILED -> PENDING with fresh attempt budget). */
    @PostMapping("/outbox/replay")
    @Transactional
    public Map<String, Object> replayDeadLetters() {
        var dead = outboxRepository.findByStatus("FAILED");
        dead.forEach(e -> {
            e.setStatus("PENDING");
            e.setAttempts(0);
            e.setAvailableAt(Instant.now());
            e.setLastError(null);
        });
        log.info("Replaying {} dead-lettered outbox events", dead.size());
        return Map.of("replayed", dead.size());
    }
}
