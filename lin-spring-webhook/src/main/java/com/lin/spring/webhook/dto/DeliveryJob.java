package com.lin.spring.webhook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lin.spring.webhook.domain.MerchantLane;

import java.time.Instant;

/**
 * 一条待投递任务。经 Jackson 序列化后进入 Redis 队列。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeliveryJob(
	String jobId,
	String eventId,
	String merchantId,
	String endpointId,
	String url,
	String secret,
	String eventType,
	String payload,
	int attempt,
	MerchantLane lane,
	Instant eventCreatedAt
) {

	public static DeliveryJob first(String eventId, String merchantId, String endpointId, String url,
									String secret, String eventType, String payload, Instant eventCreatedAt) {
		return new DeliveryJob(
			java.util.UUID.randomUUID().toString(),
			eventId, merchantId, endpointId, url, secret, eventType, payload,
			1, MerchantLane.MAIN, eventCreatedAt
		);
	}

	public DeliveryJob nextAttempt(int attempt, MerchantLane lane) {
		return new DeliveryJob(
			java.util.UUID.randomUUID().toString(),
			eventId, merchantId, endpointId, url, secret, eventType, payload,
			attempt, lane, eventCreatedAt
		);
	}

	/** 幂等键：同一事件对同一端点视为同一次投递。 */
	public String idempotencyKey() {
		return eventId + ":" + endpointId;
	}
}
