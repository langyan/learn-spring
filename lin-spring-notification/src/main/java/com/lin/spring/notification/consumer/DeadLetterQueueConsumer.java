package com.lin.spring.notification.consumer;

import com.lin.spring.notification.dto.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * DLQ 消费者：记录需人工介入的失败通知。
 */
@Slf4j
@Component
@Profile("kafka")
public class DeadLetterQueueConsumer {

	/**
	 * 消费死信队列消息。
	 */
	@KafkaListener(
		topics = "${app.notification.topics.dlq}",
		groupId = "dlq-monitor",
		containerFactory = "notificationKafkaListenerContainerFactory"
	)
	public void consume(NotificationEvent event, Acknowledgment acknowledgment) {
		log.error("[DLQ] 需人工处理 notificationId={} userId={} channel={} retryCount={} correlationId={}",
			event.notificationId(), event.userId(), event.channel(), event.retryCount(), event.correlationId());
		acknowledgment.acknowledge();
	}
}
