package com.lin.spring.webhook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * 内部业务事件（Kafka 上传输的对象）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookEvent(
	String eventId,
	String merchantId,
	String eventType,
	String payload,
	Instant createdAt
) {
	public WebhookEvent {
		if (eventId == null || eventId.isBlank()) {
			throw new IllegalArgumentException("eventId 不能为空");
		}
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
