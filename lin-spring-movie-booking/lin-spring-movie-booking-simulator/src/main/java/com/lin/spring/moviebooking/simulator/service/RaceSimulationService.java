package com.lin.spring.moviebooking.simulator.service;

import com.lin.spring.moviebooking.common.dto.HoldRequest;
import com.lin.spring.moviebooking.common.dto.HoldResponse;
import com.lin.spring.moviebooking.common.dto.RaceSummaryResponse;
import com.lin.spring.moviebooking.common.dto.SimulationRequest;
import com.lin.spring.moviebooking.common.dto.SimulationResponse;
import com.lin.spring.moviebooking.simulator.client.BookingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The proof of the whole design: N virtual users, one seat set, one starting
 * gun. Exactly one hold may win — the conditional UPDATE is the arbiter, and
 * the booking-service race-summary must afterwards report oversellDetected=0.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RaceSimulationService {

    private final BookingClient bookingClient;

    public SimulationResponse simulate(SimulationRequest request) throws InterruptedException {
        int concurrency = Math.max(1, request.concurrency());
        List<Long> seatIds = request.seatIds().stream().distinct().sorted().toList();
        long showId = request.showId();

        CyclicBarrier startGun = new CyclicBarrier(concurrency);
        CountDownLatch done = new CountDownLatch(concurrency);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        long beganAt = System.nanoTime();
        try {
            for (int i = 0; i < concurrency; i++) {
                final long userId = 10_000L + i;
                final String idemKey = "sim-" + beganAt + "-" + i;
                pool.submit(() -> {
                    try {
                        if (request.staggerMs() > 0) {
                            Thread.sleep((long) (Math.random() * request.staggerMs()));
                        }
                        startGun.await(10, TimeUnit.SECONDS);   // align, then sprint
                        HoldResponse hold = bookingClient.tryHold(
                                new HoldRequest(showId, seatIds, userId, idemKey));
                        if (hold != null) {
                            succeeded.incrementAndGet();
                        } else {
                            conflicts.incrementAndGet();
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    } catch (Exception ex) {
                        log.warn("Racer {} crashed: {}", userId, ex.getMessage());
                        failed.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            if (!done.await(60, TimeUnit.SECONDS)) {
                log.error("Simulation did not finish in 60s — booking-service unresponsive?");
            }
        } finally {
            pool.shutdownNow();
        }
        long durationMs = (System.nanoTime() - beganAt) / 1_000_000;

        long oversell = -1;
        try {
            RaceSummaryResponse summary = bookingClient.raceSummary();
            if (summary != null) {
                oversell = summary.oversellDetected();
            }
        } catch (Exception ex) {
            log.warn("Could not fetch race summary: {}", ex.getMessage());
        }

        log.info("Race on show {} seats {}: concurrency={}, succeeded={}, conflicts={}, failed={}, oversell={}, {}ms",
                showId, seatIds, concurrency, succeeded, conflicts, failed, oversell, durationMs);
        return new SimulationResponse(showId, seatIds, concurrency,
                succeeded.get(), conflicts.get(), failed.get(), oversell, durationMs);
    }
}
