package com.lin.spring.webhook.domain;

/**
 * 投递失败分类，决定是重试还是直接进 DLQ。
 */
public enum FailureType {
	/** 5xx / 408 / 429 / 超时 / IO 异常：值得重试 */
	RETRYABLE,
	/** 其余 4xx、SSRF 拦截等：重试无意义 */
	NON_RETRYABLE
}
