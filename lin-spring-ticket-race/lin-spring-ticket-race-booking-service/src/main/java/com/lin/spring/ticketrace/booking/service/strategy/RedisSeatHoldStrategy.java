package com.lin.spring.ticketrace.booking.service.strategy;

import com.lin.spring.ticketrace.booking.exception.SeatUnavailableException;
import com.lin.spring.ticketrace.booking.service.BookingIdGenerator;
import com.lin.spring.ticketrace.booking.service.RedisSeatLockService;
import com.lin.spring.ticketrace.booking.service.TicketRaceMetrics;
import com.lin.spring.ticketrace.common.dto.HoldSeatRequest;
import com.lin.spring.ticketrace.common.dto.HoldSeatResponse;
import com.lin.spring.ticketrace.common.enums.LockStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RedisSeatHoldStrategy implements SeatHoldStrategy {

    private final RedisSeatLockService redisSeatLockService;
    private final RedisSeatHoldTxService txService;
    private final BookingIdGenerator bookingIdGenerator;
    private final TicketRaceMetrics ticketRaceMetrics;

    @Override
    public LockStrategy supports() {
        return LockStrategy.REDIS_HOLD;
    }

    @Override
    public HoldSeatResponse hold(HoldSeatRequest request) {
        String bookingNo = bookingIdGenerator.nextBookingNo();
        String redisKey = "seat:" + request.showId() + ":" + request.seatCode();
        boolean acquired = redisSeatLockService.acquire(redisKey, bookingNo, Duration.ofSeconds(request.holdSeconds()));
        if (!acquired) {
            ticketRaceMetrics.incrementHoldFailure();
            throw new SeatUnavailableException(request.showId(), request.seatCode());
        }

        try {
            return txService.holdWithRedis(request, bookingNo, redisKey);
        } catch (RuntimeException exception) {
            redisSeatLockService.release(redisKey);
            ticketRaceMetrics.incrementHoldFailure();
            throw exception;
        }
    }
}
