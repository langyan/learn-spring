package com.lin.spring.moviebooking.booking.service;

import com.lin.spring.moviebooking.booking.config.BookingProperties;
import com.lin.spring.moviebooking.booking.domain.Booking;
import com.lin.spring.moviebooking.booking.domain.BookingSeat;
import com.lin.spring.moviebooking.booking.domain.Payment;
import com.lin.spring.moviebooking.booking.repository.BookingRepository;
import com.lin.spring.moviebooking.booking.repository.BookingSeatRepository;
import com.lin.spring.moviebooking.booking.repository.PaymentRepository;
import com.lin.spring.moviebooking.booking.repository.ShowSeatRepository;
import com.lin.spring.moviebooking.common.dto.WebhookPayload;
import com.lin.spring.moviebooking.common.enums.BookingStatus;
import com.lin.spring.moviebooking.common.enums.EventTopic;
import com.lin.spring.moviebooking.common.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Webhook ingestion — where money meets inventory.
 *
 * <p>Idempotency: a duplicate webhook hits a payment already in a terminal
 * state and returns silently (five deliveries, one effect).
 *
 * <p>The hard race: money captured, hold already expired and swept.
 * Blind confirm is impossible (guarded conditional UPDATE); blind refund
 * angers customers. We try a BOUNDED grace window (60s): re-hold + confirm
 * if the seats are still on the market; otherwise refund and count the loss.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    public static final String GATEWAY = "SIMULATED";

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final ShowSeatRepository showSeatRepository;
    private final OutboxAppender outbox;
    private final MovieBookingMetrics metrics;
    private final BookingProperties properties;

    @Transactional
    public void handle(WebhookPayload payload) {
        Payment payment = paymentRepository
                .findByGatewayAndGatewayRef(GATEWAY, payload.gatewayRef())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown gatewayRef: " + payload.gatewayRef()));

        // Duplicate delivery — already in a terminal state. Collapse silently.
        if (payment.getStatus() != PaymentStatus.CREATED
                && payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.UNKNOWN) {
            log.info("Duplicate webhook for gatewayRef {} ignored (status={})",
                    payload.gatewayRef(), payment.getStatus());
            return;
        }

        Booking booking = bookingRepository.findById(payment.getBookingId())
                .orElseThrow(() -> new IllegalStateException(
                        "Payment without booking: " + payment.getId()));

        if ("SUCCEEDED".equals(payload.result())) {
            confirmWithRecovery(payment, booking);
        } else {
            failBooking(payment, booking, payload.failureCode() != null
                    ? payload.failureCode() : "GATEWAY_DECLINED");
        }
    }

    // ------------------------------------------------------------------
    // Success path + grace-window recovery
    // ------------------------------------------------------------------
    private void confirmWithRecovery(Payment payment, Booking booking) {
        // Seat set traces through HELD, sweep (booking_id kept), and BOOKED.
        List<Long> seatIds = showSeatRepository.findSeatIdsByBookingId(
                booking.getShowId(), booking.getId());

        // Fast path: our hold is live and unexpired.
        if (seatIds.size() == booking.getSeatCount()) {
            int confirmed = showSeatRepository.confirmSeats(
                    booking.getShowId(), seatIds, booking.getHoldId(), booking.getId());
            if (confirmed == booking.getSeatCount()) {
                confirm(payment, booking, seatIds, false);
                return;
            }
        }

        // Hold gone (expired & swept / partially taken over). Bounded grace first.
        Instant graceDeadline = booking.getHoldExpiresAt() != null
                ? booking.getHoldExpiresAt().plusSeconds(properties.getLateWebhook().getGraceSeconds())
                : null;
        if (graceDeadline != null && Instant.now().isBefore(graceDeadline)
                && !seatIds.isEmpty()) {
            int reclaimed = showSeatRepository.confirmOrReclaimSeats(
                    booking.getShowId(), seatIds, booking.getHoldId(), booking.getId());
            if (reclaimed == booking.getSeatCount()) {
                confirm(payment, booking, seatIds, true);
                return;
            }
            if (reclaimed > 0) {
                // Partial reclaim: roll back atomically; retry may fully
                // recover or eventually fall to refund.
                throw new IllegalStateException("Partial grace reclaim for "
                        + booking.getReference() + " (" + reclaimed + "/" + booking.getSeatCount() + ")");
            }
        }

        // Seats resold or too late: keep the money? No — refund and count the loss.
        refundAfterCapture(payment, booking);
    }

    private void confirm(Payment payment, Booking booking, List<Long> seatIds, boolean recovered) {
        Instant now = Instant.now();
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setUpdatedAt(now);

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(now);

        // L3 — the append-only ledger with its unique (show_id, seat_id).
        for (Long seatId : seatIds) {
            bookingSeatRepository.save(new BookingSeat(booking.getId(), booking.getShowId(), seatId, now));
        }

        // Defensive canary: nobody else may own these seats. Should never trip.
        long foreign = bookingSeatRepository.countForeignConfirmedSeats(
                booking.getShowId(), seatIds, booking.getId());
        if (foreign > 0) {
            metrics.reportOversell((int) foreign);
            throw new IllegalStateException("OVERSELL DETECTED for booking " + booking.getReference());
        }

        Map<String, Object> event = new HashMap<>();
        event.put("bookingReference", booking.getReference());
        event.put("userId", booking.getUserId());
        event.put("showId", booking.getShowId());
        event.put("seatIds", seatIds);
        event.put("recoveredAfterExpiry", recovered);
        outbox.append(EventTopic.BOOKING_CONFIRMED, booking.getReference(), event);

        metrics.incBookingsConfirmed();
        if (recovered) {
            metrics.incLateWebhookRecovered();
        }
        log.info("Booking {} CONFIRMED ({} seats, recovered={})",
                booking.getReference(), seatIds.size(), recovered);
    }

    private void refundAfterCapture(Payment payment, Booking booking) {
        Instant now = Instant.now();
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setFailureCode("SEATS_LOST_REFUND");
        payment.setUpdatedAt(now);

        booking.setStatus(BookingStatus.FAILED);

        Map<String, Object> event = new HashMap<>();
        event.put("bookingReference", booking.getReference());
        event.put("userId", booking.getUserId());
        event.put("reason", "SEATS_LOST_AFTER_CAPTURE");
        outbox.append(EventTopic.BOOKING_REFUNDED, booking.getReference(), event);

        metrics.incLateWebhookRefunded();
        metrics.incSeatsLostAfterCapture(booking.getSeatCount());
        log.warn("Booking {} REFUNDED: payment captured after grace window, {} seats lost to market",
                booking.getReference(), booking.getSeatCount());
    }

    // ------------------------------------------------------------------
    // Failure path
    // ------------------------------------------------------------------
    private void failBooking(Payment payment, Booking booking, String failureCode) {
        Instant now = Instant.now();
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureCode(failureCode);
        payment.setUpdatedAt(now);

        booking.setStatus(BookingStatus.FAILED);

        List<Long> seatIds = showSeatRepository.findSeatIdsByHoldId(
                booking.getShowId(), booking.getHoldId());
        if (seatIds.size() == booking.getSeatCount()) {
            showSeatRepository.releaseSeats(booking.getShowId(), seatIds, booking.getHoldId());
        }
        log.info("Booking {} FAILED ({}), seats released: {}",
                booking.getReference(), failureCode, seatIds.size());
    }
}
