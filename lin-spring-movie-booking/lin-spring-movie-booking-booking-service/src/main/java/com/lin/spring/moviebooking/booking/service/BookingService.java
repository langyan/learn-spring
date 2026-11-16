package com.lin.spring.moviebooking.booking.service;

import com.lin.spring.moviebooking.booking.client.PaymentGatewayClient;
import com.lin.spring.moviebooking.booking.config.BookingProperties;
import com.lin.spring.moviebooking.booking.exception.SeatConflictException;
import com.lin.spring.moviebooking.booking.domain.Booking;
import com.lin.spring.moviebooking.booking.domain.Payment;
import com.lin.spring.moviebooking.booking.repository.BookingRepository;
import com.lin.spring.moviebooking.booking.repository.PaymentRepository;
import com.lin.spring.moviebooking.booking.repository.ShowSeatRepository;
import com.lin.spring.moviebooking.common.dto.BookingResponse;
import com.lin.spring.moviebooking.common.dto.HoldRequest;
import com.lin.spring.moviebooking.common.dto.HoldResponse;
import com.lin.spring.moviebooking.common.dto.PaymentCommand;
import com.lin.spring.moviebooking.common.dto.PaymentInitiationRequest;
import com.lin.spring.moviebooking.common.dto.PaymentInitiationResponse;
import com.lin.spring.moviebooking.common.dto.PaymentStatusResponse;
import com.lin.spring.moviebooking.common.dto.WebhookPayload;
import com.lin.spring.moviebooking.common.enums.BookingStatus;
import com.lin.spring.moviebooking.common.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The hot path. Hold semantics (deadlock avoidance: seatIds are ALWAYS
 * deduplicated and sorted before any multi-row operation).
 *
 * <p>Transaction discipline: the payment gateway is called OUTSIDE any
 * transaction; the two DB steps around it each run in their own short
 * transaction via TransactionTemplate.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    public static final String ENDPOINT_HOLD = "POST /api/v1/bookings/hold";
    public static final String ENDPOINT_PAY = "POST /api/v1/bookings/{ref}/payments";

    private final ShowSeatRepository showSeatRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final SeatLockService seatLockService;
    private final IdempotencyService idempotencyService;
    private final MovieBookingMetrics metrics;
    private final PaymentGatewayClient gatewayClient;
    private final WebhookService webhookService;
    private final BookingProperties properties;
    private final TransactionTemplate transactionTemplate;

    // ------------------------------------------------------------------
    // HOLD
    // ------------------------------------------------------------------
    @Transactional
    public HoldResponse hold(HoldRequest request) {
        // 0. Idempotent replay of a double-tapped Reserve button.
        HoldResponse cached = idempotencyService.findCached(
                request.userId(), request.idempotencyKey(), ENDPOINT_HOLD, HoldResponse.class);
        if (cached != null) {
            log.info("Idempotent hold replay for user {} key {}", request.userId(), request.idempotencyKey());
            return cached;
        }

        // 1. Deduplicate + sort: fixed lock order rules out ABBA deadlocks.
        List<Long> seatIds = request.seatIds().stream().distinct().sorted().toList();
        if (seatIds.size() != request.seatIds().size()) {
            throw new IllegalArgumentException("Duplicate seatIds in request");
        }
        if (seatIds.isEmpty() || seatIds.size() > 10) {
            throw new IllegalArgumentException("Between 1 and 10 seats per booking");
        }
        metrics.incHoldsRequested();

        long showId = request.showId();
        String holdId = "hold-" + UUID.randomUUID();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getHold().getTtlSeconds());

        // 2. L1 — Redis advisory admission check (best effort, never authoritative).
        boolean admitted = seatLockService.tryAcquire(
                showId, seatIds, holdId, Duration.ofSeconds(properties.getHold().getTtlSeconds()));
        if (!admitted) {
            metrics.incRedisAdmissionDenied();
            metrics.incSeatConflicts();
            throw new SeatConflictException("Seats locked by another user in flight");
        }

        // 3. Booking row first (same tx): show_seats.booking_id is stamped at
        //    HOLD time so late webhooks can trace the seat set after sweeping.
        BigDecimal total = showSeatRepository.sumPrice(showId, seatIds);
        Booking booking = new Booking();
        booking.setReference(generateReference(showId));
        booking.setUserId(request.userId());
        booking.setShowId(showId);
        booking.setHoldId(holdId);
        booking.setSeatCount(seatIds.size());
        booking.setTotalAmount(total);
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setIdempotencyKey(request.idempotencyKey());
        booking.setCreatedAt(now);
        booking.setHoldExpiresAt(expiresAt);
        bookingRepository.save(booking);

        // 4. L2 — the authoritative conditional UPDATE.
        int updated = showSeatRepository.holdSeats(showId, seatIds, holdId, booking.getId(), expiresAt);
        if (updated != seatIds.size()) {
            seatLockService.release(showId, seatIds, holdId);   // give back advisory locks
            metrics.incSeatConflicts();
            List<Long> unavailable = showSeatRepository.findUnavailableSeatIds(showId, seatIds);
            throw new SeatConflictException(unavailable);       // rolls the booking row back too
        }

        HoldResponse response = new HoldResponse(booking.getReference(), holdId,
                request.userId(), showId, seatIds, total, expiresAt, booking.getStatus());
        idempotencyService.remember(request.userId(), request.idempotencyKey(),
                ENDPOINT_HOLD, response);
        metrics.incHoldsSucceeded();
        log.info("Held {} seats for booking {} (holdId={})", seatIds.size(), booking.getReference(), holdId);
        return response;
    }

    // ------------------------------------------------------------------
    // PAY (gateway call strictly between two transactions)
    // ------------------------------------------------------------------
    public PaymentInitiationResponse initiatePayment(PaymentInitiationRequest request) {
        PaymentInitiationResponse cached = idempotencyService.findCached(
                request.userId(), request.idempotencyKey(), ENDPOINT_PAY, PaymentInitiationResponse.class);
        if (cached != null) {
            log.info("Idempotent payment replay for user {} key {}", request.userId(), request.idempotencyKey());
            return cached;
        }

        // TX-1: validate booking, extend hold once, create payment row.
        long paymentRowId = transactionTemplate.execute(status -> {
            Booking booking = bookingRepository.findByReference(request.bookingReference())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Booking not found: " + request.bookingReference()));
            if (booking.getUserId() != request.userId()) {
                throw new IllegalStateException("Booking belongs to another user");
            }
            if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
                throw new IllegalStateException("Booking is " + booking.getStatus()
                        + ", cannot initiate payment");
            }
            extendHoldOnce(booking);
            Payment payment = new Payment();
            payment.setBookingId(booking.getId());
            payment.setStatus(PaymentStatus.CREATED);
            payment.setAmount(booking.getTotalAmount());
            payment.setIdempotencyKey(request.idempotencyKey());
            payment.setCreatedAt(Instant.now());
            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);
            return payment.getId();
        });

        // Gateway call — NO transaction held here.
        Booking bookingView = bookingRepository.findByReference(request.bookingReference()).orElseThrow();
        PaymentInitiationResponse intent = gatewayClient.createIntent(new PaymentCommand(
                bookingView.getReference(), request.userId(), request.idempotencyKey(),
                request.channel(), bookingView.getTotalAmount(), request.scenario()));

        // TX-2: attach gatewayRef, mark PENDING, cache the response.
        transactionTemplate.executeWithoutResult(status -> {
            Payment payment = paymentRepository.findById(paymentRowId).orElseThrow();
            payment.setGatewayRef(intent.gatewayRef());
            payment.setStatus(PaymentStatus.PENDING);
            payment.setUpdatedAt(Instant.now());
            idempotencyService.remember(request.userId(), request.idempotencyKey(),
                    ENDPOINT_PAY, intent);
        });

        log.info("Payment {} initiated for booking {} (gatewayRef={})",
                intent.paymentId(), request.bookingReference(), intent.gatewayRef());
        return intent;
    }

    /**
     * Entering the payment screen extends the hold once (5min -> 10min).
     * If the hold is already gone we do NOT block the user: the webhook
     * grace path will either re-hold or refund.
     */
    private void extendHoldOnce(Booking booking) {
        if (booking.isHoldExtended()) {
            return;
        }
        Instant newExpiry = Instant.now().plusSeconds(properties.getHold().getExtendedTtlSeconds());
        List<Long> seatIds = showSeatRepository.findSeatIdsByHoldId(
                booking.getShowId(), booking.getHoldId());
        int extended = seatIds.isEmpty() ? 0
                : showSeatRepository.extendHold(booking.getShowId(), seatIds, booking.getHoldId(), newExpiry);
        if (extended == seatIds.size() && extended == booking.getSeatCount()) {
            booking.setHoldExtended(true);
            booking.setHoldExpiresAt(newExpiry);
            log.info("Hold extended for booking {} until {}", booking.getReference(), newExpiry);
        } else {
            log.warn("Hold extension missed rows for booking {} ({}/{}) — grace path will decide",
                    booking.getReference(), extended, booking.getSeatCount());
        }
    }

    // ------------------------------------------------------------------
    // CANCEL / READ
    // ------------------------------------------------------------------
    /**
     * Explicit gateway status query — the ONLY sanctioned way to resolve an
     * UNKNOWN payment outcome (lost webhook). Never assume: query.
     */
    public PaymentStatusResponse syncPaymentStatus(String reference, String gatewayRef) {
        PaymentStatusResponse result = gatewayClient.queryStatus(gatewayRef);
        switch (result.status()) {
            case SUCCEEDED, FAILED -> webhookService.handle(new WebhookPayload(
                    result.paymentId(), result.bookingReference(), result.gatewayRef(),
                    result.status().name(), result.failureCode()));
            default -> log.info("Gateway still non-terminal for {} ({}), nothing to apply",
                    gatewayRef, result.status());
        }
        return result;
    }

    @Transactional
    public BookingResponse cancel(String reference, long userId) {
        Booking booking = bookingRepository.findByReference(reference)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + reference));
        if (booking.getUserId() != userId) {
            throw new IllegalStateException("Booking belongs to another user");
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Booking is " + booking.getStatus() + ", cannot cancel");
        }
        List<Long> seatIds = showSeatRepository.findSeatIdsByBookingId(booking.getShowId(), booking.getId());
        if (seatIds.size() == booking.getSeatCount()) {
            showSeatRepository.releaseSeats(booking.getShowId(), seatIds, booking.getHoldId());
        }
        booking.setStatus(BookingStatus.CANCELLED);
        seatLockService.release(booking.getShowId(), seatIds, booking.getHoldId());
        log.info("Booking {} cancelled, {} seats released", reference, seatIds.size());
        return toResponse(booking, seatIds);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(String reference) {
        Booking booking = bookingRepository.findByReference(reference)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + reference));
        List<Long> seatIds = showSeatRepository.findSeatIdsByBookingId(
                booking.getShowId(), booking.getId());
        return toResponse(booking, seatIds);
    }

    private BookingResponse toResponse(Booking booking, List<Long> seatIds) {
        return new BookingResponse(booking.getReference(), booking.getUserId(), booking.getShowId(),
                booking.getStatus(), seatIds, booking.getTotalAmount(), booking.getCreatedAt(),
                booking.getHoldExpiresAt(), booking.getConfirmedAt());
    }

    /** Human-shareable reference printed on the ticket, e.g. 8842-778811-29F4. */
    private String generateReference(long showId) {
        return "%d-%06d-%04X".formatted(showId,
                ThreadLocalRandom.current().nextInt(1_000_000),
                ThreadLocalRandom.current().nextInt(0x10000));
    }
}
