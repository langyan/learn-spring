package com.lin.spring.notification.consumer;

import com.lin.spring.notification.domain.NotificationChannel;
import com.lin.spring.notification.dto.NotificationEvent;
import com.lin.spring.notification.service.NotificationProcessingService;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Push 渠道 Kafka Consumer Group。
 */
@Component
@Profile("kafka")
public class PushNotificationConsumer extends ChannelNotificationConsumer {

	public PushNotificationConsumer(NotificationProcessingService processingService) {
		super(processingService);
	}

	@Override
	protected NotificationChannel channel() {
		return NotificationChannel.PUSH;
	}

	/**
	 * 消费各优先级 Topic 中的 Push 通知。
	 */
	@KafkaListener(
		topics = {
			"${app.notification.topics.critical}",
			"${app.notification.topics.transactional}",
			"${app.notification.topics.marketing}",
			"${app.notification.topics.retry}"
		},
		groupId = "push-worker",
		containerFactory = "notificationKafkaListenerContainerFactory"
	)
	public void consume(NotificationEvent event, Acknowledgment acknowledgment) {
		handle(event, acknowledgment);
	}
}
