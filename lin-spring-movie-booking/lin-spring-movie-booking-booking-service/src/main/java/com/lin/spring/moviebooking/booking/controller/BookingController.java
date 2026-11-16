package com.lin.spring.moviebooking.booking.controller;

import com.lin.spring.moviebooking.booking.service.BookingService;
import com.lin.spring.moviebooking.booking.service.ShowInventoryService;
import com.lin.spring.moviebooking.common.dto.BookingResponse;
import com.lin.spring.moviebooking.common.dto.HoldRequest;
import com.lin.spring.moviebooking.common.dto.HoldResponse;
import com.lin.spring.moviebooking.common.dto.PaymentInitiationRequest;
import com.lin.spring.moviebooking.common.dto.PaymentInitiationResponse;
import com.lin.spring.moviebooking.common.dto.PaymentStatusResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final ShowInventoryService showInventoryService;

    @PostMapping("/hold")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldResponse hold(@Valid @RequestBody HoldRequest request) {
        return bookingService.hold(request);
    }

    @PostMapping("/{reference}/payments")
    public PaymentInitiationResponse initiatePayment(
            @PathVariable String reference,
            @Valid @RequestBody PaymentInitiationRequest request) {
        return bookingService.initiatePayment(request);
    }

    @PostMapping("/{reference}/cancel")
    public BookingResponse cancel(@PathVariable String reference,
                                  @RequestParam long userId) {
        return bookingService.cancel(reference, userId);
    }

    /** Resolve a stuck/UNKNOWN payment by explicitly querying the gateway. */
    @PostMapping("/{reference}/payments/{gatewayRef}/sync")
    public PaymentStatusResponse syncPayment(@PathVariable String reference,
                                             @PathVariable String gatewayRef) {
        return bookingService.syncPaymentStatus(reference, gatewayRef);
    }

    @GetMapping("/{reference}")
    public BookingResponse getBooking(@PathVariable String reference) {
        return bookingService.getBooking(reference);
    }

    /** Manual (re)publish for a show's inventory — idempotent. */
    @PostMapping("/admin/shows/{showId}/publish")
    public String publishShow(@PathVariable long showId) {
        int rows = showInventoryService.publishShow(showId);
        return rows == 0 ? "already published" : rows + " seat rows created";
    }
}
