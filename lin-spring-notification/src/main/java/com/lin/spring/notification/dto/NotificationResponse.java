package com.lin.spring.notification.dto;

import com.lin.spring.notification.domain.NotificationStatus;

/**
 * 发送通知 API 响应体。
 */
public record NotificationResponse(
	String notificationId,
	NotificationStatus status,
	String correlationId,
	String message
) {
}
