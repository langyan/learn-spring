package com.lin.spring.moviebooking.payment.domain;

import com.lin.spring.moviebooking.common.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** In-memory simulated gateway intent (state survives restarts only in demos). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntent {

    private String paymentId;
    private String gatewayRef;
    private String bookingReference;
    private Long userId;
    private BigDecimal amount;
    private String channel;
    private String scenario;
    private String idempotencyKey;
    private volatile PaymentStatus status;
    private String failureCode;
    private Instant createdAt;
    private Instant completedAt;
}
