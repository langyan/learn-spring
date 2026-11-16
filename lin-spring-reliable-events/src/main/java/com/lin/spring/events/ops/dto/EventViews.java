package com.lin.spring.events.ops.dto;

import com.lin.spring.events.inbox.entity.InboxEvent;
import com.lin.spring.events.inbox.entity.InboxStatus;
import com.lin.spring.events.outbox.entity.OutboxEvent;
import com.lin.spring.events.outbox.entity.OutboxStatus;

import java.time.Instant;

/**
 * 运维查询视图：暴露状态机、重试计划与锁信息，让「事件后来怎么样了」可直接检查。
 */
public final class EventViews {

	private EventViews() {
	}

	/**
	 * Outbox 记录视图。
	 */
	public record OutboxView(
		Long id, String eventId, String eventType, String aggregateType, String aggregateId,
		String source, String correlationId, OutboxStatus status, int attempts,
		Instant createdAt, Instant availableAt, Instant publishedAt, String lastError,
		String lockOwner, Instant lockedAt, String payload
	) {
		public static OutboxView from(OutboxEvent e) {
			return new OutboxView(e.getId(), e.getEventId(), e.getEventType(), e.getAggregateType(),
				e.getAggregateId(), e.getSource(), e.getCorrelationId(), e.getStatus(), e.getAttempts(),
				e.getCreatedAt(), e.getAvailableAt(), e.getPublishedAt(), e.getLastError(),
				e.getLockOwner(), e.getLockedAt(), e.getPayload());
		}
	}

	/**
	 * Inbox 记录视图。
	 */
	public record InboxView(
		Long id, String eventId, String eventType, String source, String correlationId,
		InboxStatus status, int attempts, Instant receivedAt, Instant availableAt,
		Instant processedAt, String lastError, String lockOwner, Instant lockedAt, String payload
	) {
		public static InboxView from(InboxEvent e) {
			return new InboxView(e.getId(), e.getEventId(), e.getEventType(), e.getSource(),
				e.getCorrelationId(), e.getStatus(), e.getAttempts(), e.getReceivedAt(),
				e.getAvailableAt(), e.getProcessedAt(), e.getLastError(),
				e.getLockOwner(), e.getLockedAt(), e.getPayload());
		}
	}

	/**
	 * 端到端链路视图：同一 correlationId 下的 Outbox 与 Inbox 记录。
	 */
	public record TraceView(String correlationId, java.util.List<OutboxView> outbox, java.util.List<InboxView> inbox) {
	}
}
