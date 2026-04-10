package com.lin.spring.ticketrace.booking.service;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class BookingIdGenerator {

    public String nextBookingNo() {
        return "BOOK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public String nextHoldToken() {
        return "HOLD-" + UUID.randomUUID();
    }
}
