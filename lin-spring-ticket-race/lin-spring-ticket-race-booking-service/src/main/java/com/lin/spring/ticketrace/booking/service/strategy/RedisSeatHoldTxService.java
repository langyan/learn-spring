package com.lin.spring.ticketrace.booking.service.strategy;

import com.lin.spring.ticketrace.booking.entity.SeatInventory;
import com.lin.spring.ticketrace.booking.exception.SeatUnavailableException;
import com.lin.spring.ticketrace.booking.repository.SeatInventoryRepository;
import com.lin.spring.ticketrace.booking.service.BookingIdGenerator;
import com.lin.spring.ticketrace.booking.service.BookingLifecycleService;
import com.lin.spring.ticketrace.common.dto.HoldSeatRequest;
import com.lin.spring.ticketrace.common.dto.HoldSeatResponse;
import com.lin.spring.ticketrace.common.enums.SeatInventoryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RedisSeatHoldTxService {

    private final SeatInventoryRepository seatInventoryRepository;
    private final BookingIdGenerator bookingIdGenerator;
    private final BookingLifecycleService bookingLifecycleService;

    @Transactional
    public HoldSeatResponse holdWithRedis(HoldSeatRequest request, String bookingNo, String redisKey) {
        SeatInventory seatInventory = seatInventoryRepository.findByShowIdAndSeatCode(request.showId(), request.seatCode())
                .orElseThrow(() -> new SeatUnavailableException(request.showId(), request.seatCode()));

        if (seatInventory.getStatus() != SeatInventoryStatus.AVAILABLE) {
            throw new SeatUnavailableException(request.showId(), request.seatCode());
        }

        String holdToken = bookingIdGenerator.nextHoldToken();
        Instant expiresAt = Instant.now().plusSeconds(request.holdSeconds());
        return bookingLifecycleService.createHold(seatInventory, request, bookingNo, holdToken, redisKey, expiresAt);
    }
}
