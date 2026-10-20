package com.lin.spring.notification.dto;

import com.lin.spring.notification.domain.NotificationChannel;
import com.lin.spring.notification.domain.NotificationType;

import java.util.Map;

/**
 * Kafka 通知事件载荷（按 userId 分区保证同用户有序）。
 */
public record NotificationEvent(
	String notificationId,
	String userId,
	NotificationType type,
	NotificationChannel channel,
	String templateId,
	Map<String, Object> data,
	String idempotencyKey,
	int retryCount,
	String correlationId
) {
}
