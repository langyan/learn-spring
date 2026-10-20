package com.lin.spring.webhook.domain;

/**
 * 一次投递尝试的最终处置结果。
 */
public enum OutcomeKind {
	/** 收到 2xx */
	DELIVERED,
	/** 可重试失败，已按指数退避重新排入延迟队列 */
	RETRY_SCHEDULED,
	/** 不可重试或重试超限，进入 DLQ */
	DEAD_LETTERED,
	/** 目的地长期离线，主动取消 */
	CANCELLED,
	/** 幂等去重命中，本次跳过 */
	SKIPPED_DUPLICATE,
	/** 因商户并发配额耗尽或泳道受限，稍后再调度 */
	DEFERRED
}
