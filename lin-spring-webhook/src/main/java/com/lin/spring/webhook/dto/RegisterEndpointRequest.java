package com.lin.spring.webhook.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 商户注册 Webhook 端点的入参。
 */
public class RegisterEndpointRequest {

	@NotBlank(message = "url 不能为空")
	private String url;

	/** HMAC 签名密钥；为空时系统自动生成 */
	private String secret;

	/** 订阅的事件类型；为空表示订阅全部 */
	private List<String> eventTypes;

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	public List<String> getEventTypes() {
		return eventTypes;
	}

	public void setEventTypes(List<String> eventTypes) {
		this.eventTypes = eventTypes;
	}
}
