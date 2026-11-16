package com.lin.spring.events.inbox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 收件箱表（event_inbox）：消费侧的持久事件处理历史。
 * <p>
 * event_id 唯一约束是幂等边界——同一事件重复投递只允许一条记录，副作用只发生一次。
 * 处理状态、尝试次数、下次重试时间与最后错误全部显式落库，可查询、可重放。
 * </p>
 */
@Entity
@Table(name = "event_inbox", indexes = {
	@Index(name = "idx_inbox_dispatch", columnList = "status,available_at,id"),
	@Index(name = "idx_inbox_correlation", columnList = "correlation_id")
})
@Getter
@Setter
@NoArgsConstructor
public class InboxEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 全局唯一事件 ID：幂等边界，唯一约束兜底并发插入。 */
	@Column(name = "event_id", nullable = false, unique = true, length = 64)
	private String eventId;

	@Column(name = "event_type", nullable = false, length = 128)
	private String eventType;

	@Column(name = "source", length = 64)
	private String source;

	@Column(name = "correlation_id", length = 64)
	private String correlationId;

	@Lob
	@Column(name = "payload", nullable = false, columnDefinition = "CLOB")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private InboxStatus status = InboxStatus.RECEIVED;

	@Column(name = "attempts", nullable = false)
	private int attempts;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	/** 下次可处理时间：早于等于 now 才会被重试器扫到；耗尽置 FAILED 后为 null。 */
	@Column(name = "available_at")
	private Instant availableAt;

	@Column(name = "processed_at")
	private Instant processedAt;

	@Column(name = "last_error", length = 1024)
	private String lastError;

	@Column(name = "lock_owner", length = 64)
	private String lockOwner;

	@Column(name = "locked_at")
	private Instant lockedAt;

	/**
	 * 构造一条新登记的 Inbox 记录：RECEIVED、attempts=0、availableAt=now。
	 */
	public InboxEvent(String eventId, String eventType, String source, String correlationId, String payload) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.source = source;
		this.correlationId = correlationId;
		this.payload = payload;
		this.status = InboxStatus.RECEIVED;
		this.attempts = 0;
		this.receivedAt = Instant.now();
		this.availableAt = this.receivedAt;
	}
}
