package com.lin.spring.webhook.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 内部服务提交事件的入参。
 */
public class IngestEventRequest {

	@NotBlank(message = "merchantId 不能为空")
	private String merchantId;

	@NotBlank(message = "eventType 不能为空")
	private String eventType;

	/** 业务载荷（JSON 字符串），可为空 */
	private String payload;

	/** 调用方传入的事件 ID，用于端到端幂等；为空时由系统生成 */
	private String eventId;

	public String getMerchantId() {
		return merchantId;
	}

	public void setMerchantId(String merchantId) {
		this.merchantId = merchantId;
	}

	public String getEventType() {
		return eventType;
	}

	public void setEventType(String eventType) {
		this.eventType = eventType;
	}

	public String getPayload() {
		return payload;
	}

	public void setPayload(String payload) {
		this.payload = payload;
	}

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}
}
