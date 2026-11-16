package com.lin.spring.moviebooking.common.enums;

/**
 * Kafka topics used by the transactional outbox. Kafka carries notification
 * events only — never the booking/payment critical path.
 */
public enum EventTopic {
    BOOKING_CONFIRMED("movie-booking.booking-confirmed"),
    BOOKING_REFUNDED("movie-booking.booking-refunded"),
    BOOKING_EXPIRED("movie-booking.booking-expired");

    private final String topicName;

    EventTopic(String topicName) {
        this.topicName = topicName;
    }

    public String topicName() {
        return topicName;
    }
}
