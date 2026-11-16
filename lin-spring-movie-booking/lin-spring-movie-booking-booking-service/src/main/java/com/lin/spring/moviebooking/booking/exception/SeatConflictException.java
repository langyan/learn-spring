package com.lin.spring.moviebooking.booking.exception;

import java.util.List;

/** 409: some requested seats are not available. */
public class SeatConflictException extends RuntimeException {

    private final List<Long> unavailableSeats;

    public SeatConflictException(List<Long> unavailableSeats) {
        super("Seats not available: " + unavailableSeats);
        this.unavailableSeats = unavailableSeats;
    }

    public SeatConflictException(String message) {
        super(message);
        this.unavailableSeats = List.of();
    }

    public List<Long> getUnavailableSeats() {
        return unavailableSeats;
    }
}
