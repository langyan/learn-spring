package com.lin.spring.moviebooking.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "movie-booking.payment-gateway")
public class PaymentGatewayProperties {

    /** Simulated UPI processing latency (ms). */
    private long upiDelayMs = 2000;
    /** Simulated CARD processing latency (ms). */
    private long cardDelayMs = 500;
    /** LATE scenario: webhook lands well after a short demo hold expires. */
    private long lateDelayMs = 7000;
    /** DUPLICATE scenario: how many times the webhook is redelivered. */
    private int duplicateDeliveries = 5;
    /** Webhook delivery retries (1s/2s/4s backoff) before giving up. */
    private int webhookMaxRetries = 3;
    private String bookingServiceId = "movie-booking-booking-service";
}
