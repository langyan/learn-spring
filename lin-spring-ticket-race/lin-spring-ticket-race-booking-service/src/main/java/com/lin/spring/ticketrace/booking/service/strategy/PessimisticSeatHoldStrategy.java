package com.lin.spring.ticketrace.booking.service.strategy;

import com.lin.spring.ticketrace.booking.service.TicketRaceMetrics;
import com.lin.spring.ticketrace.common.dto.HoldSeatRequest;
import com.lin.spring.ticketrace.common.dto.HoldSeatResponse;
import com.lin.spring.ticketrace.common.enums.LockStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PessimisticSeatHoldStrategy implements SeatHoldStrategy {

    private final PessimisticSeatHoldTxService txService;
    private final TicketRaceMetrics ticketRaceMetrics;

    @Override
    public LockStrategy supports() {
        return LockStrategy.PESSIMISTIC;
    }

    @Override
    public HoldSeatResponse hold(HoldSeatRequest request) {
        try {
            return txService.hold(request);
        } catch (RuntimeException exception) {
            ticketRaceMetrics.incrementHoldFailure();
            throw exception;
        }
    }
}
