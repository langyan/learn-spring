package com.lin.spring.events.outbox.entity;

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
 * 事务性发件箱表（event_outbox）：与业务数据在同一本地事务中写入，
 * 要么一起提交，要么一起回滚；派发状态、重试计划与锁信息全部显式落库。
 * <p>
 * 索引：(status, available_at) 支撑派发扫描，(correlation_id) 支撑链路排查。
 * </p>
 */
@Entity
@Table(name = "event_outbox", indexes = {
	@Index(name = "idx_outbox_dispatch", columnList = "status,available_at,id"),
	@Index(name = "idx_outbox_correlation", columnList = "correlation_id")
})
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 全局唯一事件 ID：消费侧幂等边界。 */
	@Column(name = "event_id", nullable = false, unique = true, length = 64)
	private String eventId;

	@Column(name = "event_type", nullable = false, length = 128)
	private String eventType;

	@Column(name = "aggregate_type", nullable = false, length = 64)
	private String aggregateType;

	@Column(name = "aggregate_id", nullable = false, length = 64)
	private String aggregateId;

	/** 事件来源服务名。 */
	@Column(name = "source", nullable = false, length = 64)
	private String source;

	/** 关联 ID：串起同一业务动作触发的全部事件，供跨表排查。 */
	@Column(name = "correlation_id", length = 64)
	private String correlationId;

	/** 人类可读的持久化载荷（JSON）。 */
	@Lob
	@Column(name = "payload", nullable = false, columnDefinition = "CLOB")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private OutboxStatus status = OutboxStatus.PENDING;

	/** 已尝试投递次数（申领成功即 +1）。 */
	@Column(name = "attempts", nullable = false)
	private int attempts;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/** 下次可投递时间：早于等于 now 才会被派发器扫到；耗尽置 FAILED 后为 null（不再自动重试）。 */
	@Column(name = "available_at")
	private Instant availableAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "last_error", length = 1024)
	private String lastError;

	/** 当前持有者（实例 ID）；仅 PROCESSING 状态非空。 */
	@Column(name = "lock_owner", length = 64)
	private String lockOwner;

	@Column(name = "locked_at")
	private Instant lockedAt;

	/**
	 * 构造一条待派发 Outbox 记录：PENDING、attempts=0、availableAt=now。
	 */
	public OutboxEvent(String eventId, String eventType, String aggregateType, String aggregateId,
					   String source, String correlationId, String payload) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.source = source;
		this.correlationId = correlationId;
		this.payload = payload;
		this.status = OutboxStatus.PENDING;
		this.attempts = 0;
		this.createdAt = Instant.now();
		this.availableAt = this.createdAt;
	}
}
