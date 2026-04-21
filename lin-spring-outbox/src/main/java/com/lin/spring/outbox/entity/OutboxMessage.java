package com.lin.spring.outbox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Transactional Outbox 表：与业务数据在同一数据库事务中写入。
 */
@Entity
@Table(name = "outbox_messages")
@Getter
@Setter
@NoArgsConstructor
public class OutboxMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, unique = true, length = 64)
	private String eventId;

	@Column(name = "aggregate_type", nullable = false, length = 64)
	private String aggregateType;

	@Column(name = "aggregate_id", nullable = false, length = 64)
	private String aggregateId;

	@Column(name = "event_type", nullable = false, length = 128)
	private String eventType;

	@Lob
	@Column(name = "payload", nullable = false, columnDefinition = "CLOB")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private OutboxStatus status = OutboxStatus.PENDING;

	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	@Column(name = "error_message", length = 1024)
	private String errorMessage;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "processed_at")
	private Instant processedAt;

	/**
	 * 构造一条待投递 Outbox 记录。
	 */
	public OutboxMessage(String eventId, String aggregateType, String aggregateId, String eventType, String payload) {
		this.eventId = eventId;
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
		this.status = OutboxStatus.PENDING;
		this.retryCount = 0;
		this.createdAt = Instant.now();
	}
}
