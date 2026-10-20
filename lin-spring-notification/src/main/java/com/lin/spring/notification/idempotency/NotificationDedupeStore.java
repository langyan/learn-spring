package com.lin.spring.notification.idempotency;

/**
 * 消费端幂等存储抽象（Redis 或内存实现）。
 */
public interface NotificationDedupeStore {

	/**
	 * 尝试占用幂等键，成功表示首次处理。
	 *
	 * @param idempotencyKey 幂等键
	 * @return true 表示首次处理
	 */
	boolean tryClaim(String idempotencyKey);

	/**
	 * 标记处理完成。
	 *
	 * @param idempotencyKey 幂等键
	 */
	void markDone(String idempotencyKey);

	/**
	 * 释放占用（处理失败且需重试时）。
	 *
	 * @param idempotencyKey 幂等键
	 */
	void release(String idempotencyKey);
}
