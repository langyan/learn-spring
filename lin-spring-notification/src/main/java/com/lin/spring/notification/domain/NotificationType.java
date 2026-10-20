package com.lin.spring.notification.domain;

/**
 * 通知业务类型，决定优先级 Topic 路由。
 */
public enum NotificationType {
	/** 一次性验证码，最高优先级 */
	OTP,
	/** 安全类告警 */
	SECURITY,
	/** 交易/订单确认 */
	TRANSACTIONAL,
	/** 营销推广 */
	MARKETING
}
