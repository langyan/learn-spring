package com.lin.spring.events.outbox;

import com.lin.spring.events.outbox.entity.OutboxEvent;

/**
 * 投递通道抽象：Outbox 派发器把事件交给具体实现送达 broker/总线。
 * <ul>
 *   <li>InMemoryEventBus（默认，!kafka）：日志 + 回灌同进程 Inbox，闭环演示</li>
 *   <li>KafkaEventPublisher（kafka profile）：真实 Kafka 发送，acks=all 同步确认</li>
 * </ul>
 */
public interface EventPublisher {

	/**
	 * 投递一条事件。实现应在失败时抛出异常，由派发服务负责重试编排。
	 */
	void publish(OutboxEvent event) throws Exception;
}
