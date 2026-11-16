package com.lin.spring.moviebooking.common.dto;

import com.lin.spring.moviebooking.common.enums.PaymentStatus;

public record PaymentInitiationResponse(
        String paymentId,
        String bookingReference,
        String gatewayRef,
        PaymentStatus status,
        String clientSecret
) {}
