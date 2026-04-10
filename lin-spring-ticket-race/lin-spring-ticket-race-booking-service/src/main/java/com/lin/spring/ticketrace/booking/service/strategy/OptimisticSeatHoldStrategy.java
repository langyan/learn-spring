package com.lin.spring.ticketrace.booking.service.strategy;

import com.lin.spring.ticketrace.booking.service.TicketRaceMetrics;
import com.lin.spring.ticketrace.common.dto.HoldSeatRequest;
import com.lin.spring.ticketrace.common.dto.HoldSeatResponse;
import com.lin.spring.ticketrace.common.enums.LockStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OptimisticSeatHoldStrategy implements SeatHoldStrategy {

    private static final int MAX_RETRIES = 3;

    private final OptimisticSeatHoldTxService txService;
    private final TicketRaceMetrics ticketRaceMetrics;

    @Override
    public LockStrategy supports() {
        return LockStrategy.OPTIMISTIC;
    }

    @Override
    public HoldSeatResponse hold(HoldSeatRequest request) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return txService.holdOnce(request);
            } catch (ObjectOptimisticLockingFailureException exception) {
                ticketRaceMetrics.incrementOptimisticConflict();
                if (attempt == MAX_RETRIES) {
                    ticketRaceMetrics.incrementHoldFailure();
                    throw exception;
                }
            } catch (RuntimeException exception) {
                ticketRaceMetrics.incrementHoldFailure();
                throw exception;
            }
        }
        ticketRaceMetrics.incrementHoldFailure();
        throw new IllegalStateException("Optimistic hold failed unexpectedly");
    }
}
