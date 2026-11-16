package com.lin.spring.events.inbox.entity;

/**
 * Inbox 事件状态机：
 * <pre>
 * Broker 投递 ──> RECEIVED ──claim──> PROCESSING ──success──> PROCESSED
 *                   ^                   |
 *                   |                failure（未耗尽）→ 回到 RECEIVED，availableAt = now + backoff
 * 重复投递            |                   |
 * （eventId 已存在    |                failure（耗尽）→ FAILED（等待人工重放）
 *  → DUPLICATE）   stale 回收
 * </pre>
 */
public enum InboxStatus {
	/** 已登记（新事件或等待重试）。 */
	RECEIVED,
	/** 处理中（已被某实例/线程申领）。 */
	PROCESSING,
	/** 处理成功，副作用已发生。 */
	PROCESSED,
	/** 重试耗尽，等待排查与人工重放。 */
	FAILED
}
