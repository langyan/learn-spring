package com.lin.spring.ticketrace.booking.exception;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(String bookingNo) {
        super("Booking not found: " + bookingNo);
    }
}
