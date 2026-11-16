package com.lin.spring.moviebooking.booking.service;

import com.lin.spring.moviebooking.booking.config.BookingProperties;
import com.lin.spring.moviebooking.booking.domain.Booking;
import com.lin.spring.moviebooking.booking.repository.BookingRepository;
import com.lin.spring.moviebooking.booking.repository.ShowSeatRepository;
import com.lin.spring.moviebooking.common.enums.BookingStatus;
import com.lin.spring.moviebooking.common.enums.EventTopic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Housekeeping, not correctness: every conditional UPDATE treats an expired
 * HELD row as reclaimable, so if this scheduler dies the system stays
 * correct — seats just return to the pool lazily on the next hold attempt.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldExpiryScheduler {

    private final ShowSeatRepository showSeatRepository;
    private final BookingRepository bookingRepository;
    private final OutboxAppender outbox;
    private final MovieBookingMetrics metrics;
    private final BookingProperties properties;

    @Scheduled(fixedDelayString = "${movie-booking.expiry.scan-delay-ms:5000}",
            initialDelayString = "${movie-booking.expiry.scan-delay-ms:5000}")
    public void sweepExpiredHolds() {
        List<String> expiredHoldIds = showSeatRepository.findExpiredHoldIds(
                properties.getExpiry().getSweepBatchSize());
        for (String holdId : expiredHoldIds) {
            try {
                sweepOne(holdId);
            } catch (Exception ex) {
                log.error("Sweep failed for holdId {}: {}", holdId, ex.getMessage());
            }
        }
        if (!expiredHoldIds.isEmpty()) {
            log.debug("Swept {} expired holds", expiredHoldIds.size());
        }
    }

    @Transactional
    public void sweepOne(String holdId) {
        // Release rows still HELD under this expired hold. booking_id is kept
        // on purpose: a late webhook uses it to trace the seat set for the
        // grace-window recovery.
        int released = showSeatRepository.releaseExpiredHold(holdId);
        if (released == 0) {
            return;
        }
        metrics.incExpiredHoldsReleased(released);

        bookingRepository.findByHoldId(holdId).ifPresent(booking -> {
            if (booking.getStatus() == BookingStatus.PENDING_PAYMENT) {
                booking.setStatus(BookingStatus.EXPIRED);
                Map<String, Object> event = new HashMap<>();
                event.put("bookingReference", booking.getReference());
                event.put("userId", booking.getUserId());
                event.put("seatsReleased", released);
                outbox.append(EventTopic.BOOKING_EXPIRED, booking.getReference(), event);
                log.info("Booking {} EXPIRED, {} seats returned to pool",
                        booking.getReference(), released);
            }
        });
    }
}
