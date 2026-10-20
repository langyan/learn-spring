package com.lin.spring.webhook.dto;

import com.lin.spring.webhook.entity.DeliveryAttemptEntity;

/**
 * 投递尝试流水的对外视图。
 */
public record DeliveryAttemptView(
	long id,
	String eventId,
	String endpointId,
	int attempt,
	Integer statusCode,
	long latencyMs,
	boolean success,
	String error,
	String outcome
) {
	public static DeliveryAttemptView from(DeliveryAttemptEntity entity) {
		return new DeliveryAttemptView(
			entity.getId(),
			entity.getEventId(),
			entity.getEndpointId(),
			entity.getAttempt(),
			entity.getStatusCode(),
			entity.getLatencyMs(),
			entity.isSuccess(),
			entity.getError(),
			entity.getOutcome()
		);
	}
}
