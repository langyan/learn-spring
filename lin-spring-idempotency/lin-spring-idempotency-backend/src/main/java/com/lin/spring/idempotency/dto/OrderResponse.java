package com.lin.spring.idempotency.dto;

public record OrderResponse(
	String orderNo,
	String customerId,
	String productName,
	int quantity,
	String orderStatus,
	String idempotencyKey,
	String eventId,
	boolean replayed,
	String message
) {
	public OrderResponse asReplayed(String replayMessage) {
		return new OrderResponse(
			orderNo,
			customerId,
			productName,
			quantity,
			orderStatus,
			idempotencyKey,
			eventId,
			true,
			replayMessage
		);
	}
}
