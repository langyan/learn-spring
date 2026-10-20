package com.lin.spring.notification.publisher;

import com.lin.spring.notification.dto.NotificationEvent;
import com.lin.spring.notification.service.NotificationProcessingService;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 本地模式：异步进程内投递，无需 Kafka。
 */
@Component
@Profile("!kafka")
public class InProcessNotificationEventPublisher implements NotificationEventPublisher {

	private final NotificationProcessingService processingService;

	public InProcessNotificationEventPublisher(@Lazy NotificationProcessingService processingService) {
		this.processingService = processingService;
	}

	@Override
	@Async
	public void publish(String topic, NotificationEvent event) {
		processingService.process(event);
	}
}
