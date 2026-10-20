package com.lin.spring.webhook.dispatcher;

import com.lin.spring.webhook.config.WebhookProperties;
import com.lin.spring.webhook.dto.WebhookEvent;
import com.lin.spring.webhook.service.EventDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka 事件消费者。只在非 local profile 下生效。
 */
@Slf4j
@Component
@Profile("!local")
public class EventConsumer {

	private final EventDispatcher dispatcher;
	private final WebhookProperties properties;

	public EventConsumer(EventDispatcher dispatcher, WebhookProperties properties) {
		this.dispatcher = dispatcher;
		this.properties = properties;
	}

	/**
	 * 消费事件并展开为投递任务。手动提交 offset，保证「没入队就不丢事件」。
	 */
	@KafkaListener(
		topics = "${app.webhook.topics.events}",
		groupId = "${spring.kafka.consumer.group-id:webhook-workers}",
		containerFactory = "webhookKafkaListenerContainerFactory")
	public void onEvent(ConsumerRecord<String, WebhookEvent> record, Acknowledgment acknowledgment) {
		WebhookEvent event = record.value();
		try {
			dispatcher.dispatch(event);
			acknowledgment.acknowledge();
		} catch (RuntimeException ex) {
			log.error("事件分发失败，保留 offset 等待重投 eventId={}", event == null ? "unknown" : event.eventId(), ex);
		}
	}
}
