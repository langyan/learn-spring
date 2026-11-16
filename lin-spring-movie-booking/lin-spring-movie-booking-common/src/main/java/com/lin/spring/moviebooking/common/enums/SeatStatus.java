package com.lin.spring.moviebooking.common.enums;

/**
 * Per-(show, seat) inventory status. The hot rows in show_seats.
 */
public enum SeatStatus {
    /** Open for sale. */
    AVAILABLE,
    /** Held by a user until hold_expires_at; reclaimable once expired. */
    HELD,
    /** Confirmed by payment; ownership recorded in booking_seats ledger. */
    BOOKED,
    /** Blocked by ops (broken seat, private screening); never sellable. */
    BLOCKED
}
