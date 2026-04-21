package com.lin.spring.outbox.outbox;

import com.lin.spring.outbox.entity.OutboxMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 使用 Kafka 投递 Outbox（需激活 {@code kafka} profile 且本地或环境提供 Broker）。
 */
@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaOutboxMessagePublisher implements OutboxMessagePublisher {

	private final KafkaTemplate<String, String> kafkaTemplate;

	@Value("${app.outbox.kafka.topic:order-events}")
	private String topic;

	@Value("${app.outbox.kafka.send-timeout-seconds:5}")
	private long sendTimeoutSeconds;

	@Override
	public void publish(OutboxMessage message) throws Exception {
		CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(
			topic,
			message.getAggregateId(),
			message.getPayload()
		);
		future.get(sendTimeoutSeconds, TimeUnit.SECONDS);
		log.info("Published outbox to Kafka: eventId={} topic={}", message.getEventId(), topic);
	}
}
