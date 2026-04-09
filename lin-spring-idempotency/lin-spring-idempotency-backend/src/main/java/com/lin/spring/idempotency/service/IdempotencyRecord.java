package com.lin.spring.idempotency.service;

import com.lin.spring.idempotency.dto.OrderResponse;

import java.time.Instant;

public record IdempotencyRecord(
	IdempotencyStatus status,
	String requestHash,
	OrderResponse responseBody,
	String orderNo,
	Instant updatedAt
) {
}
