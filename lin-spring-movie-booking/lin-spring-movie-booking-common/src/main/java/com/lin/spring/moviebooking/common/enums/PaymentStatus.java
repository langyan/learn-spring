package com.lin.spring.moviebooking.common.enums;

/**
 * Payment lifecycle as seen by booking-service.
 *
 * <p>UNKNOWN is resolved only by an explicit status query against the gateway —
 * never by assumption. Treating a pending gateway call as failure is how you
 * either charge without a ticket or ticket without a charge.</p>
 */
public enum PaymentStatus {
    CREATED,
    PENDING,
    SUCCEEDED,
    FAILED,
    /** Money captured but seats lost; refund issued to the customer. */
    REFUNDED,
    /** Gateway outcome unknown (timeout, callback lost); resolve via status query. */
    UNKNOWN
}
