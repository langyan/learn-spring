package com.lin.spring.moviebooking.booking.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Race dashboard counters. oversellDetected must stay 0 forever;
 * seatsLostAfterCapture measures late-webhook grace-window effectiveness.
 */
@Component
public class MovieBookingMetrics {

    private final AtomicLong holdsRequested = new AtomicLong();
    private final AtomicLong holdsSucceeded = new AtomicLong();
    private final AtomicLong seatConflicts = new AtomicLong();
    private final AtomicLong oversellDetected = new AtomicLong();
    private final AtomicLong bookingsConfirmed = new AtomicLong();
    private final AtomicLong lateWebhookRecovered = new AtomicLong();
    private final AtomicLong lateWebhookRefunded = new AtomicLong();
    private final AtomicLong seatsLostAfterCapture = new AtomicLong();
    private final AtomicLong expiredHoldsReleased = new AtomicLong();
    private final AtomicLong redisAdmissionDenied = new AtomicLong();

    private final Counter oversellCounter;

    public MovieBookingMetrics(MeterRegistry registry) {
        this.oversellCounter = Counter.builder("movie_booking_oversell_detected")
                .description("Must remain zero; any tick means the invariant broke")
                .register(registry);
    }

    public void incHoldsRequested() { holdsRequested.incrementAndGet(); }
    public void incHoldsSucceeded() { holdsSucceeded.incrementAndGet(); }
    public void incSeatConflicts() { seatConflicts.incrementAndGet(); }
    public void incRedisAdmissionDenied() { redisAdmissionDenied.incrementAndGet(); }
    public void incExpiredHoldsReleased(int seats) { expiredHoldsReleased.addAndGet(seats); }
    public void incBookingsConfirmed() { bookingsConfirmed.incrementAndGet(); }
    public void incLateWebhookRecovered() { lateWebhookRecovered.incrementAndGet(); }
    public void incLateWebhookRefunded() { lateWebhookRefunded.incrementAndGet(); }

    public void incSeatsLostAfterCapture(int seats) {
        seatsLostAfterCapture.addAndGet(seats);
    }

    /** Sounding the alarm: any non-zero value is a page-worthy event. */
    public void reportOversell(int seats) {
        oversellDetected.addAndGet(seats);
        oversellCounter.increment(seats);
    }

    public long holdsRequested() { return holdsRequested.get(); }
    public long holdsSucceeded() { return holdsSucceeded.get(); }
    public long seatConflicts() { return seatConflicts.get(); }
    public long oversellDetected() { return oversellDetected.get(); }
    public long bookingsConfirmed() { return bookingsConfirmed.get(); }
    public long lateWebhookRecovered() { return lateWebhookRecovered.get(); }
    public long lateWebhookRefunded() { return lateWebhookRefunded.get(); }
    public long seatsLostAfterCapture() { return seatsLostAfterCapture.get(); }
    public long expiredHoldsReleased() { return expiredHoldsReleased.get(); }
    public long redisAdmissionDenied() { return redisAdmissionDenied.get(); }
}
