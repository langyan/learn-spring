package com.lin.spring.idempotency.service;

import com.lin.spring.idempotency.dto.OrderResponse;

public record IdempotencyDecision(
	IdempotencyDecisionType type,
	OrderResponse cachedResponse
) {
	public static IdempotencyDecision claimed() {
		return new IdempotencyDecision(IdempotencyDecisionType.CLAIMED, null);
	}

	public static IdempotencyDecision completed(OrderResponse cachedResponse) {
		return new IdempotencyDecision(IdempotencyDecisionType.COMPLETED, cachedResponse);
	}

	public static IdempotencyDecision processing() {
		return new IdempotencyDecision(IdempotencyDecisionType.PROCESSING, null);
	}

	public static IdempotencyDecision conflict() {
		return new IdempotencyDecision(IdempotencyDecisionType.CONFLICT, null);
	}

	public enum IdempotencyDecisionType {
		CLAIMED,
		COMPLETED,
		PROCESSING,
		CONFLICT
	}
}
