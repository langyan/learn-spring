package com.lin.spring.ticketrace.booking.service;

import com.lin.spring.ticketrace.common.dto.RaceMetricsResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class TicketRaceMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter holdSuccessCounter;
    private final Counter holdFailureCounter;
    private final Counter optimisticConflictCounter;
    private final Counter expiredReleaseCounter;
    private final Counter confirmSuccessCounter;

    public TicketRaceMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.holdSuccessCounter = meterRegistry.counter("ticket.race.hold.success.total");
        this.holdFailureCounter = meterRegistry.counter("ticket.race.hold.failure.total");
        this.optimisticConflictCounter = meterRegistry.counter("ticket.race.optimistic.conflict.total");
        this.expiredReleaseCounter = meterRegistry.counter("ticket.race.expired.release.total");
        this.confirmSuccessCounter = meterRegistry.counter("ticket.race.confirm.success.total");
    }

    public void incrementHoldSuccess() {
        holdSuccessCounter.increment();
    }

    public void incrementHoldFailure() {
        holdFailureCounter.increment();
    }

    public void incrementOptimisticConflict() {
        optimisticConflictCounter.increment();
    }

    public void incrementExpiredRelease() {
        expiredReleaseCounter.increment();
    }

    public void incrementConfirmSuccess() {
        confirmSuccessCounter.increment();
    }

    public RaceMetricsResponse summary() {
        return new RaceMetricsResponse(
                count("ticket.race.hold.success.total"),
                count("ticket.race.hold.failure.total"),
                count("ticket.race.optimistic.conflict.total"),
                count("ticket.race.expired.release.total"),
                count("ticket.race.confirm.success.total")
        );
    }

    private double count(String meterName) {
        Counter counter = meterRegistry.find(meterName).counter();
        return counter == null ? 0D : counter.count();
    }
}
