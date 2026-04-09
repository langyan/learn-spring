package com.lin.spring.idempotency.dto;

public record ReplayEventResponse(
	String orderNo,
	String eventId,
	String message
) {
}
