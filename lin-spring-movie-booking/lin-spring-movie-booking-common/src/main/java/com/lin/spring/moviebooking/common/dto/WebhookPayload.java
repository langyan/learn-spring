package com.lin.spring.moviebooking.common.dto;

/**
 * Webhook payload delivered by payment-service to booking-service.
 * Duplicate deliveries are collapsed by the unique (gateway, gateway_ref)
 * constraint on payments — five identical webhooks, one row affected.
 */
public record WebhookPayload(
        String paymentId,
        String bookingReference,
        String gatewayRef,
        /** SUCCEEDED or FAILED. */
        String result,
        String failureCode
) {}
