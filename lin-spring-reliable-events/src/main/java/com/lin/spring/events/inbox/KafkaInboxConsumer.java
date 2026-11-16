package com.lin.spring.events.inbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * kafka profile 消费者：手动 ack 构成 at-least-once。
 * <ul>
 *   <li>处理成功才 {@code acknowledge()}；异常时不 ack，容器稍后重投</li>
 *   <li>重复投递由 Inbox（eventId 唯一）去重，副作用只发生一次</li>
 *   <li>处理失败不阻塞分区：Inbox 登记成功即 ack，重试由 Inbox 持久化重试接管</li>
 * </ul>
 */
@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaInboxConsumer {

	private final InboxService inboxService;

	private final ObjectMapper objectMapper;

	@KafkaListener(
		topics = "${app.events.kafka.topic}",
		containerFactory = "eventsKafkaListenerContainerFactory"
	)
	public void consume(org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record,
						Acknowledgment acknowledgment) {
		String payload = record.value();
		try {
			JsonNode envelope = objectMapper.readTree(payload);
			String eventId = envelope.path("eventId").asText(null);
			String eventType = envelope.path("eventType").asText("UNKNOWN");
			String source = envelope.path("source").asText(null);
			String correlationId = envelope.path("correlationId").asText(null);

			if (eventId == null || eventId.isBlank()) {
				// 格式坏消息：无法登记幂等，直接 ack 避免 poison message 无限重投（生产可转 DLQ）
				log.error("Malformed event envelope, skipping: topic={} offset={}",
					record.topic(), record.offset());
				acknowledgment.acknowledge();
				return;
			}

			InboxService.ReceiveResult result = inboxService.receive(eventId, eventType, source, correlationId, payload);
			log.debug("Kafka consumed: eventId={} result={} partition={} offset={}",
				eventId, result, record.partition(), record.offset());
			// DUPLICATE 也 ack：这是正常的 at-least-once 行为，不是错误
			acknowledgment.acknowledge();
		} catch (Exception e) {
			// 登记或处理编排本身异常：不 ack，交还容器重投（Inbox 已保证幂等）
			log.error("Kafka consume error, will redeliver: topic={} offset={} err={}",
				record.topic(), record.offset(), e.getMessage(), e);
		}
	}
}
