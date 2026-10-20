package com.lin.spring.webhook.service;

/**
 * 事件级幂等存储。
 *
 * <p>它解决的是「平台内部重复投递」：消费端重复拉到同一条消息、调度器重复捞取等。
 * 它<b>不能</b>解决跨网络的 exactly-once —— 那部分只能靠接收端按事件 ID 去重（见追问 4）。</p>
 */
public interface IdempotencyService {

	/**
	 * 抢占处理权。
	 *
	 * @param key 幂等键
	 * @return true 表示抢到，可以处理
	 */
	boolean tryClaim(String key);

	/** 标记处理已完成（终态），后续重复事件一律忽略。 */
	void markDone(String key);

	/** 释放处理权，允许后续重试。 */
	void release(String key);
}
