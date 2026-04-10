package com.lin.spring.ticketrace.booking.service;

import com.lin.spring.ticketrace.booking.exception.StrategyUnavailableException;
import com.lin.spring.ticketrace.booking.service.strategy.SeatHoldStrategy;
import com.lin.spring.ticketrace.common.dto.BookingResponse;
import com.lin.spring.ticketrace.common.dto.HoldSeatRequest;
import com.lin.spring.ticketrace.common.dto.HoldSeatResponse;
import com.lin.spring.ticketrace.common.dto.PaymentCommand;
import com.lin.spring.ticketrace.common.dto.PaymentResult;
import com.lin.spring.ticketrace.common.enums.BookingStatus;
import com.lin.spring.ticketrace.common.enums.LockStrategy;
import com.lin.spring.ticketrace.common.enums.PaymentStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private final Map<LockStrategy, SeatHoldStrategy> strategyMap;
    private final BookingLifecycleService bookingLifecycleService;
    private final PaymentGatewayClient paymentGatewayClient;

    public BookingService(
            java.util.List<SeatHoldStrategy> strategies,
            BookingLifecycleService bookingLifecycleService,
            PaymentGatewayClient paymentGatewayClient
    ) {
        this.strategyMap = strategies.stream().collect(Collectors.toMap(SeatHoldStrategy::supports, Function.identity()));
        this.bookingLifecycleService = bookingLifecycleService;
        this.paymentGatewayClient = paymentGatewayClient;
    }

    public HoldSeatResponse holdSeat(HoldSeatRequest request) {
        SeatHoldStrategy strategy = strategyMap.get(request.strategy());
        if (strategy == null) {
            throw new StrategyUnavailableException("Unsupported strategy: " + request.strategy());
        }
        return strategy.hold(request);
    }

    public BookingResponse getBooking(String bookingNo) {
        return bookingLifecycleService.getBooking(bookingNo);
    }

    public BookingResponse pay(String bookingNo, PaymentCommand command) {
        BookingResponse current = bookingLifecycleService.getBooking(bookingNo);
        if (current.status() == BookingStatus.CONFIRMED) {
            return current;
        }
        if (current.status() != BookingStatus.PENDING_PAYMENT || current.expiresAt().isBefore(Instant.now())) {
            return bookingLifecycleService.cancel(bookingNo, "booking expired before payment");
        }

        PaymentResult paymentResult = paymentGatewayClient.process(new PaymentCommand(
                bookingNo,
                command.delayMillis(),
                command.forceFailure(),
                command.forceTimeout()
        ));

        if (paymentResult.status() == PaymentStatus.SUCCESS) {
            return bookingLifecycleService.confirmPayment(bookingNo, paymentResult);
        }

        return bookingLifecycleService.failPayment(bookingNo, paymentResult);
    }

    public BookingResponse cancel(String bookingNo) {
        return bookingLifecycleService.cancel(bookingNo, "cancelled by user");
    }
}
