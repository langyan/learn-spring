package com.lin.spring.webhook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 每一次投递尝试的流水，供商户排查与审计。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "delivery_attempts")
public class DeliveryAttemptEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 64)
	private String eventId;

	@Column(length = 64)
	private String endpointId;

	@Column(nullable = false)
	private int attempt;

	/** HTTP 状态码，连接失败时为 0 */
	private Integer statusCode;

	private long latencyMs;

	private boolean success;

	@Column(length = 512)
	private String error;

	/** 本次尝试后的处置结果 */
	@Column(length = 32)
	private String outcome;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	public DeliveryAttemptEntity(String eventId, String endpointId, int attempt) {
		this.eventId = eventId;
		this.endpointId = endpointId;
		this.attempt = attempt;
	}
}
