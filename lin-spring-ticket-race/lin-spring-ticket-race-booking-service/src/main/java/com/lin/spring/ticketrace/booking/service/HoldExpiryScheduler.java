package com.lin.spring.ticketrace.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class HoldExpiryScheduler {

    private final BookingLifecycleService bookingLifecycleService;

    @Scheduled(fixedDelayString = "${ticket-race.expiry.scan-delay-ms}")
    public void releaseExpiredHolds() {
        int released = bookingLifecycleService.releaseExpiredBookings(Instant.now());
        if (released > 0) {
            log.info("Released {} expired holds", released);
        }
    }
}
