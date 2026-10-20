package com.lin.spring.notification.publisher;

import com.lin.spring.notification.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Kafka 模式事件发布，按 userId 分区保证同用户有序。
 */
@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaNotificationEventPublisher implements NotificationEventPublisher {

	private final KafkaTemplate<String, NotificationEvent> notificationKafkaTemplate;

	@Value("${app.notification.kafka.send-timeout-seconds:5}")
	private long sendTimeoutSeconds;

	@Override
	public void publish(String topic, NotificationEvent event) {
		try {
			notificationKafkaTemplate.send(topic, event.userId(), event)
				.get(sendTimeoutSeconds, TimeUnit.SECONDS);
		} catch (TimeoutException ex) {
			throw new IllegalStateException("Kafka 发布超时: " + event.notificationId(), ex);
		} catch (Exception ex) {
			log.error("Kafka 发布失败 notificationId={} topic={}", event.notificationId(), topic, ex);
			throw new IllegalStateException("Kafka 发布失败: " + event.notificationId(), ex);
		}
	}
}
