package com.lin.spring.webhook.exception;

/**
 * 端点不存在。
 */
public class EndpointNotFoundException extends RuntimeException {

	public EndpointNotFoundException(String endpointId) {
		super("Webhook 端点不存在: " + endpointId);
	}
}
