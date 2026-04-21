package com.lin.spring.outbox.outbox;

import com.lin.spring.outbox.entity.OutboxMessage;

/**
 * 将 Outbox 中的消息投递到外部消息系统（Kafka、MQ 或日志演示实现）。
 */
public interface OutboxMessagePublisher {

	/**
	 * 投递单条 Outbox 消息。
	 *
	 * @param message 待投递记录
	 * @throws Exception 网络或 Broker 异常时抛出，由中继层重试
	 */
	void publish(OutboxMessage message) throws Exception;
}
