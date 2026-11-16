package com.lin.spring.moviebooking.common.enums;

/**
 * Booking lifecycle: PENDING_PAYMENT -> CONFIRMED / EXPIRED / FAILED / CANCELLED.
 */
public enum BookingStatus {
    /** Seats held, waiting for payment. */
    PENDING_PAYMENT,
    /** Payment captured and seats confirmed. */
    CONFIRMED,
    /** Hold expired before payment; seats returned to pool. */
    EXPIRED,
    /** Payment failed or unrecoverable; seats returned to pool. */
    FAILED,
    /** User cancelled; seats returned to pool. */
    CANCELLED
}
