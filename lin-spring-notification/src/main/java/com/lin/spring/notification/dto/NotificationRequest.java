package com.lin.spring.notification.dto;

import com.lin.spring.notification.domain.NotificationChannel;
import com.lin.spring.notification.domain.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

/**
 * 发送通知 API 请求体。
 */
public record NotificationRequest(
	@NotBlank String userId,
	@NotNull NotificationType type,
	@NotNull NotificationChannel channel,
	String templateId,
	Map<String, Object> data,
	String idempotencyKey,
	Instant scheduledAt
) {
}
