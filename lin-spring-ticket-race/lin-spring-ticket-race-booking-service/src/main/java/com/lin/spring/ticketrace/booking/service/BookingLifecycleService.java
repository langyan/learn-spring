package com.lin.spring.ticketrace.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.spring.ticketrace.booking.entity.Booking;
import com.lin.spring.ticketrace.booking.entity.OutboxEvent;
import com.lin.spring.ticketrace.booking.entity.PaymentRecord;
import com.lin.spring.ticketrace.booking.entity.SeatHold;
import com.lin.spring.ticketrace.booking.entity.SeatInventory;
import com.lin.spring.ticketrace.booking.enums.OutboxStatus;
import com.lin.spring.ticketrace.booking.enums.SeatHoldStatus;
import com.lin.spring.ticketrace.booking.exception.BookingNotFoundException;
import com.lin.spring.ticketrace.booking.repository.BookingRepository;
import com.lin.spring.ticketrace.booking.repository.OutboxEventRepository;
import com.lin.spring.ticketrace.booking.repository.PaymentRecordRepository;
import com.lin.spring.ticketrace.booking.repository.SeatHoldRepository;
import com.lin.spring.ticketrace.booking.repository.SeatInventoryRepository;
import com.lin.spring.ticketrace.common.dto.BookingResponse;
import com.lin.spring.ticketrace.common.dto.HoldSeatRequest;
import com.lin.spring.ticketrace.common.dto.HoldSeatResponse;
import com.lin.spring.ticketrace.common.dto.PaymentResult;
import com.lin.spring.ticketrace.common.enums.BookingStatus;
import com.lin.spring.ticketrace.common.enums.PaymentStatus;
import com.lin.spring.ticketrace.common.enums.SeatInventoryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BookingLifecycleService {

    private final BookingRepository bookingRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final RedisSeatLockService redisSeatLockService;
    private final TicketRaceMetrics ticketRaceMetrics;
    private final ObjectMapper objectMapper;

    public HoldSeatResponse createHold(
            SeatInventory seatInventory,
            HoldSeatRequest request,
            String bookingNo,
            String holdToken,
            String redisKey,
            Instant expiresAt
    ) {
        seatInventory.setStatus(SeatInventoryStatus.HELD);
        seatInventory.setHeldBookingNo(bookingNo);
        seatInventory.setHoldExpiresAt(expiresAt);
        seatInventoryRepository.save(seatInventory);

        Booking booking = new Booking();
        booking.setBookingNo(bookingNo);
        booking.setUserId(request.userId());
        booking.setShowId(request.showId());
        booking.setSeatCode(request.seatCode());
        booking.setLockStrategy(request.strategy());
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
        booking.setExpiresAt(expiresAt);
        bookingRepository.save(booking);

        SeatHold seatHold = new SeatHold();
        seatHold.setBookingNo(bookingNo);
        seatHold.setHoldToken(holdToken);
        seatHold.setShowId(request.showId());
        seatHold.setSeatCode(request.seatCode());
        seatHold.setStatus(SeatHoldStatus.ACTIVE);
        seatHold.setExpiresAt(expiresAt);
        seatHold.setRedisKey(redisKey);
        seatHoldRepository.save(seatHold);

        createOutboxEvent(bookingNo, "SEAT_HELD", Map.of(
                "showId", request.showId(),
                "seatCode", request.seatCode(),
                "strategy", request.strategy().name(),
                "expiresAt", expiresAt.toString()
        ));

        ticketRaceMetrics.incrementHoldSuccess();
        return new HoldSeatResponse(
                bookingNo,
                holdToken,
                request.userId(),
                request.showId(),
                request.seatCode(),
                request.strategy(),
                BookingStatus.PENDING_PAYMENT,
                expiresAt
        );
    }

    @Transactional
    public BookingResponse confirmPayment(String bookingNo, PaymentResult paymentResult) {
        Booking booking = getBookingEntity(bookingNo);
        savePaymentRecord(bookingNo, paymentResult);

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return toResponse(booking);
        }

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            return toResponse(booking);
        }

        SeatInventory seatInventory = getSeatInventory(booking);
        SeatHold seatHold = getSeatHold(bookingNo);

        seatInventory.setStatus(SeatInventoryStatus.BOOKED);
        seatInventory.setHoldExpiresAt(null);
        seatInventoryRepository.save(seatInventory);

        seatHold.setStatus(SeatHoldStatus.CONFIRMED);
        seatHoldRepository.save(seatHold);

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setLastFailureReason(null);
        bookingRepository.save(booking);

        redisSeatLockService.release(seatHold.getRedisKey());
        createOutboxEvent(bookingNo, "BOOKING_CONFIRMED", Map.of("bookingNo", bookingNo));
        ticketRaceMetrics.incrementConfirmSuccess();
        return toResponse(booking);
    }

    @Transactional
    public BookingResponse failPayment(String bookingNo, PaymentResult paymentResult) {
        Booking booking = getBookingEntity(bookingNo);
        savePaymentRecord(bookingNo, paymentResult);
        releaseBooking(booking, paymentResult.message(), BookingStatus.PAYMENT_FAILED, SeatHoldStatus.RELEASED);
        return toResponse(booking);
    }

    @Transactional
    public BookingResponse cancel(String bookingNo, String reason) {
        Booking booking = getBookingEntity(bookingNo);
        releaseBooking(booking, reason, BookingStatus.CANCELLED, SeatHoldStatus.RELEASED);
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(String bookingNo) {
        return toResponse(getBookingEntity(bookingNo));
    }

    @Transactional
    public int releaseExpiredBookings(Instant now) {
        List<SeatHold> expiredHolds = seatHoldRepository.findByStatusAndExpiresAtBefore(SeatHoldStatus.ACTIVE, now);
        for (SeatHold seatHold : expiredHolds) {
            Booking booking = getBookingEntity(seatHold.getBookingNo());
            if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
                continue;
            }
            releaseBooking(booking, "hold expired", BookingStatus.EXPIRED, SeatHoldStatus.EXPIRED);
            ticketRaceMetrics.incrementExpiredRelease();
        }
        return expiredHolds.size();
    }

    private void releaseBooking(Booking booking, String reason, BookingStatus bookingStatus, SeatHoldStatus holdStatus) {
        SeatInventory seatInventory = getSeatInventory(booking);
        SeatHold seatHold = getSeatHold(booking.getBookingNo());

        seatInventory.setStatus(SeatInventoryStatus.AVAILABLE);
        seatInventory.setHeldBookingNo(null);
        seatInventory.setHoldExpiresAt(null);
        seatInventoryRepository.save(seatInventory);

        seatHold.setStatus(holdStatus);
        seatHoldRepository.save(seatHold);

        booking.setStatus(bookingStatus);
        booking.setLastFailureReason(reason);
        bookingRepository.save(booking);

        redisSeatLockService.release(seatHold.getRedisKey());
        createOutboxEvent(booking.getBookingNo(), "BOOKING_RELEASED", Map.of(
                "bookingNo", booking.getBookingNo(),
                "status", bookingStatus.name(),
                "reason", reason
        ));
    }

    private void savePaymentRecord(String bookingNo, PaymentResult paymentResult) {
        PaymentRecord record = new PaymentRecord();
        record.setBookingNo(bookingNo);
        record.setStatus(paymentResult.status());
        record.setMessage(paymentResult.message());
        record.setProcessedAt(paymentResult.processedAt());
        paymentRecordRepository.save(record);
    }

    private Booking getBookingEntity(String bookingNo) {
        return bookingRepository.findByBookingNo(bookingNo)
                .orElseThrow(() -> new BookingNotFoundException(bookingNo));
    }

    private SeatInventory getSeatInventory(Booking booking) {
        return seatInventoryRepository.findByShowIdAndSeatCode(booking.getShowId(), booking.getSeatCode())
                .orElseThrow(() -> new BookingNotFoundException(booking.getBookingNo()));
    }

    private SeatHold getSeatHold(String bookingNo) {
        return seatHoldRepository.findByBookingNo(bookingNo)
                .orElseThrow(() -> new BookingNotFoundException(bookingNo));
    }

    private BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getBookingNo(),
                booking.getUserId(),
                booking.getShowId(),
                booking.getSeatCode(),
                booking.getLockStrategy(),
                booking.getStatus(),
                booking.getExpiresAt(),
                booking.getCreatedAt(),
                booking.getLastFailureReason()
        );
    }

    private void createOutboxEvent(String aggregateId, String eventType, Map<String, String> payload) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("BOOKING");
        outboxEvent.setAggregateId(aggregateId);
        outboxEvent.setEventType(eventType);
        outboxEvent.setPayload(toJson(payload));
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEventRepository.save(outboxEvent);
    }

    private String toJson(Map<String, String> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize outbox payload", exception);
        }
    }
}
