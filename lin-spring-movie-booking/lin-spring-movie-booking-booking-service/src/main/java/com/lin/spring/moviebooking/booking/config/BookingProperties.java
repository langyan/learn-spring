package com.lin.spring.moviebooking.booking.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "movie-booking")
public class BookingProperties {

    private Hold hold = new Hold();
    private LateWebhook lateWebhook = new LateWebhook();
    private Payment payment = new Payment();
    private Movie movie = new Movie();
    private Expiry expiry = new Expiry();
    private Outbox outbox = new Outbox();

    @Getter
    @Setter
    public static class Hold {
        /** Base hold TTL: conversion friendliness vs inventory fluidity. */
        private long ttlSeconds = 300;
        /** Extended once when the user enters the payment screen. */
        private long extendedTtlSeconds = 600;
    }

    @Getter
    @Setter
    public static class LateWebhook {
        /** Bounded re-hold window after hold expiry for late successful webhooks. */
        private long graceSeconds = 60;
    }

    @Getter
    @Setter
    public static class Payment {
        private String serviceId = "movie-booking-payment-service";
    }

    @Getter
    @Setter
    public static class Movie {
        private String serviceId = "movie-booking-movie-service";
    }

    @Getter
    @Setter
    public static class Expiry {
        private long scanDelayMs = 5000;
        private int sweepBatchSize = 200;
    }

    @Getter
    @Setter
    public static class Outbox {
        private long pollIntervalMs = 2000;
        private int batchSize = 50;
        private int maxAttempts = 3;
        private long baseDelayMs = 1000;
        private long maxDelayMs = 30000;
        /** PROCESSING locks older than this are considered crashed. */
        private long staleLockSeconds = 60;
    }
}
