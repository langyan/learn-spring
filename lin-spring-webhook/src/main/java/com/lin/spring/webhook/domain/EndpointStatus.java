package com.lin.spring.webhook.domain;

/**
 * 商户注册的 Webhook 端点状态。
 */
public enum EndpointStatus {
	/** 正常投递 */
	NORMAL,
	/** URL 未通过 SSRF 校验，禁止投递 */
	BLOCKED,
	/** 商户主动停用，或收到 410 Gone 后被自动停用 */
	DISABLED
}
