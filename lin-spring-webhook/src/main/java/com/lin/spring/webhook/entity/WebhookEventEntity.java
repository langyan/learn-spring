package com.lin.spring.webhook.entity;

import com.lin.spring.webhook.domain.DeliveryStatus;
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
 * 内部事件。一旦被接受落库，就一定会被尝试投递。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "webhook_events")
public class WebhookEventEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 64)
	private String eventId;

	@Column(nullable = false, length = 64)
	private String merchantId;

	@Column(length = 64)
	private String eventType;

	@Column(length = 8192)
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private DeliveryStatus status = DeliveryStatus.PENDING;

	/** 已尝试次数 */
	@Column(nullable = false)
	private int attempt = 0;

	/** 下次投递时间（延迟队列的排序依据） */
	private Instant nextAttemptAt;

	@Column(length = 512)
	private String lastError;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	private Instant updatedAt = Instant.now();
}
