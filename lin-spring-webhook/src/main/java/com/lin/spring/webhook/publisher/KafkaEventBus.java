package com.lin.spring.webhook.publisher;

import com.lin.spring.webhook.config.WebhookProperties;
import com.lin.spring.webhook.dto.WebhookEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka 事件总线。
 */
@Slf4j
@Component
@Profile("!local")
public class KafkaEventBus implements EventBus {

	private final KafkaTemplate<String, WebhookEvent> kafkaTemplate;
	private final WebhookProperties properties;

	public KafkaEventBus(KafkaTemplate<String, WebhookEvent> kafkaTemplate, WebhookProperties properties) {
		this.kafkaTemplate = kafkaTemplate;
		this.properties = properties;
	}

	@Override
	public void publish(WebhookEvent event) {
		kafkaTemplate.send(properties.getTopics().getEvents(), event.merchantId(), event)
			.whenComplete((result, ex) -> {
				if (ex != null) {
					log.error("事件发送 Kafka 失败 eventId={}", event.eventId(), ex);
				}
			});
	}
}
