package com.lin.spring.notification.entity;

import com.lin.spring.notification.domain.NotificationChannel;
import com.lin.spring.notification.domain.NotificationStatus;
import com.lin.spring.notification.domain.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 通知主表实体，记录投递意图与当前状态。
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class NotificationEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(nullable = false, length = 64)
	private String userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationChannel channel;

	@Column(length = 64)
	private String templateId;

	@Lob
	@Column(columnDefinition = "CLOB")
	private String payloadJson;

	@Column(nullable = false, unique = true, length = 128)
	private String idempotencyKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationStatus status;

	@Column(length = 64)
	private String correlationId;

	private Instant scheduledAt;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant updatedAt;

	/**
	 * 创建待发送通知实体。
	 */
	public NotificationEntity(
		String id,
		String userId,
		NotificationType type,
		NotificationChannel channel,
		String templateId,
		String payloadJson,
		String idempotencyKey,
		NotificationStatus status,
		String correlationId,
		Instant scheduledAt
	) {
		this.id = id;
		this.userId = userId;
		this.type = type;
		this.channel = channel;
		this.templateId = templateId;
		this.payloadJson = payloadJson;
		this.idempotencyKey = idempotencyKey;
		this.status = status;
		this.correlationId = correlationId;
		this.scheduledAt = scheduledAt;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}
}
