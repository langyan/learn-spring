package com.lin.spring.outbox.entity;

/**
 * Outbox 记录生命周期状态。
 */
public enum OutboxStatus {

	/** 已落库，等待中继投递 */
	PENDING,

	/** 已成功投递到消息系统 */
	PUBLISHED,

	/** 超过最大重试次数仍失败 */
	FAILED
}
