package com.lin.spring.ticketrace.booking.controller;

import com.lin.spring.ticketrace.booking.service.BookingService;
import com.lin.spring.ticketrace.common.dto.BookingResponse;
import com.lin.spring.ticketrace.common.dto.HoldSeatRequest;
import com.lin.spring.ticketrace.common.dto.HoldSeatResponse;
import com.lin.spring.ticketrace.common.dto.PaymentCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/hold")
    public HoldSeatResponse holdSeat(@Valid @RequestBody HoldSeatRequest request) {
        return bookingService.holdSeat(request);
    }

    @GetMapping("/{bookingNo}")
    public BookingResponse getBooking(@PathVariable String bookingNo) {
        return bookingService.getBooking(bookingNo);
    }

    @PostMapping("/{bookingNo}/pay")
    public BookingResponse pay(@PathVariable String bookingNo, @Valid @RequestBody PaymentCommand command) {
        return bookingService.pay(bookingNo, command);
    }

    @PostMapping("/{bookingNo}/cancel")
    public BookingResponse cancel(@PathVariable String bookingNo) {
        return bookingService.cancel(bookingNo);
    }
}
