package com.lin.spring.events.outbox.entity;

/**
 * Outbox 事件状态机：
 * <pre>
 * PENDING ──claim──> PROCESSING ──success──> PUBLISHED
 *    ^                   |
 *    |                failure（未耗尽）→ 回到 PENDING，availableAt = now + backoff
 *    |                   |
 *  stale 回收            failure（耗尽）→ FAILED（availableAt = null，停止自动重试，可人工重放）
 * </pre>
 */
public enum OutboxStatus {
	/** 已随业务事务落库，等待派发。 */
	PENDING,
	/** 已被某实例申领（写入 lockOwner/lockedAt），正在投递。 */
	PROCESSING,
	/** 已成功投递到通道。 */
	PUBLISHED,
	/** 重试耗尽或人工终止，等待排查与重放。 */
	FAILED
}
