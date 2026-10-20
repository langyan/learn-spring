package com.lin.spring.webhook.entity;

import com.lin.spring.webhook.domain.EndpointStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 商户注册的 Webhook 端点。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpointEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 64)
	private String endpointId;

	@Column(nullable = false, length = 64)
	private String merchantId;

	/** 商户提供的接收地址 */
	@Column(nullable = false, length = 1024)
	private String url;

	/** HMAC 签名密钥 */
	@Column(length = 256)
	private String secret;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private EndpointStatus status = EndpointStatus.NORMAL;

	/** 订阅的事件类型，逗号分隔；为空表示订阅全部 */
	@Column(length = 512)
	private String eventTypes;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	public boolean subscribes(String eventType) {
		if (eventTypes == null || eventTypes.isBlank()) {
			return true;
		}
		if (eventType == null) {
			return false;
		}
		for (String subscribed : eventTypes.split(",")) {
			if (subscribed.trim().equalsIgnoreCase(eventType)) {
				return true;
			}
		}
		return false;
	}
}
