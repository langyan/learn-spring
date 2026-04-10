package com.lin.spring.ticketrace.booking.exception;

public class SeatUnavailableException extends RuntimeException {

    public SeatUnavailableException(String showId, String seatCode) {
        super("Seat is no longer available: " + showId + " / " + seatCode);
    }
}
