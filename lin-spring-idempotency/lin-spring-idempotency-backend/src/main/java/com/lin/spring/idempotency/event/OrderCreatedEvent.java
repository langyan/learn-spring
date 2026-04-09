package com.lin.spring.idempotency.event;

import java.time.Instant;

public record OrderCreatedEvent(
	String eventId,
	String orderNo,
	String customerId,
	String productName,
	int quantity,
	Instant createdAt
) {
}
