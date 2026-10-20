package com.lin.spring.notification.service;

import com.lin.spring.notification.config.NotificationProperties;
import com.lin.spring.notification.dto.NotificationEvent;
import com.lin.spring.notification.publisher.NotificationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * 重试与 DLQ 路由服务。
 */
@Slf4j
@Service
public class RetryService {

	private final NotificationProperties properties;
	private final NotificationEventPublisher eventPublisher;

	public RetryService(
		NotificationProperties properties,
		@Lazy NotificationEventPublisher eventPublisher
	) {
		this.properties = properties;
		this.eventPublisher = eventPublisher;
	}

	/**
	 * 处理可重试失败：未超限则进 retry topic，否则进 DLQ。
	 *
	 * @param event 原始事件
	 */
	public void handleRetryableFailure(NotificationEvent event) {
		int nextRetry = event.retryCount() + 1;
		if (nextRetry >= properties.getRetry().getMaxAttempts()) {
			log.warn("通知进入 DLQ notificationId={} retries={}", event.notificationId(), nextRetry);
			eventPublisher.publish(properties.getTopics().getDlq(), withRetryCount(event, nextRetry));
			return;
		}
		int backoffIndex = Math.min(nextRetry - 1, properties.getRetry().getBackoffSeconds().size() - 1);
		int backoffSeconds = properties.getRetry().getBackoffSeconds().get(backoffIndex);
		log.info("通知进入重试队列 notificationId={} attempt={} backoffSeconds={}",
			event.notificationId(), nextRetry, backoffSeconds);
		sleepQuietly(backoffSeconds * 1000L);
		eventPublisher.publish(properties.getTopics().getRetry(), withRetryCount(event, nextRetry));
	}

	private NotificationEvent withRetryCount(NotificationEvent event, int retryCount) {
		return new NotificationEvent(
			event.notificationId(),
			event.userId(),
			event.type(),
			event.channel(),
			event.templateId(),
			event.data(),
			event.idempotencyKey(),
			retryCount,
			event.correlationId()
		);
	}

	private void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
