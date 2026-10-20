package com.lin.spring.webhook.dto;

import com.lin.spring.webhook.domain.FailureType;

/**
 * 一次 HTTP 投递尝试的结果。
 */
public record DeliveryResult(
	boolean success,
	int statusCode,
	long latencyMs,
	String error,
	FailureType failureType
) {

	public static DeliveryResult ok(int statusCode, long latencyMs) {
		return new DeliveryResult(true, statusCode, latencyMs, null, null);
	}

	public static DeliveryResult failure(int statusCode, long latencyMs, String error, FailureType failureType) {
		return new DeliveryResult(false, statusCode, latencyMs, error, failureType);
	}
}
