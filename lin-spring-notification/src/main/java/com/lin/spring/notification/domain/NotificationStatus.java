package com.lin.spring.notification.domain;

/**
 * 通知状态机。
 */
public enum NotificationStatus {
	PENDING,
	SCHEDULED,
	SENT,
	FAILED,
	BLOCKED
}
