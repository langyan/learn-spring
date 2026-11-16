package com.lin.spring.events.outbox;

import com.lin.spring.events.config.EventsProperties;
import com.lin.spring.events.outbox.entity.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * kafka profile 投递通道：同步等待 broker 确认（acks=all），
 * 确认超时/失败抛异常交给派发编排重试——保证「标记 PUBLISHED 前事件确实已送达」。
 */
@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {

	private final KafkaTemplate<String, String> eventsKafkaTemplate;

	private final EventsProperties properties;

	@Override
	public void publish(OutboxEvent event) throws Exception {
		String topic = properties.getKafka().getTopic();
		int timeout = properties.getKafka().getSendTimeoutSeconds();
		// key = eventId：同事件固定分区，保证消费侧顺序性要求时可扩展
		eventsKafkaTemplate.send(topic, event.getEventId(), event.getPayload())
			.get(timeout, TimeUnit.SECONDS);
		log.debug("Kafka published: topic={} eventId={}", topic, event.getEventId());
	}
}
