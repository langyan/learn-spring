package com.lin.spring.notification.consumer;

import com.lin.spring.notification.domain.NotificationChannel;
import com.lin.spring.notification.dto.NotificationEvent;
import com.lin.spring.notification.service.NotificationProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.support.Acknowledgment;

/**
 * 渠道 Consumer 公共处理逻辑。
 */
@Slf4j
@RequiredArgsConstructor
public abstract class ChannelNotificationConsumer {

	private final NotificationProcessingService processingService;

	/**
	 * 子类声明负责的渠道。
	 */
	protected abstract NotificationChannel channel();

	/**
	 * 过滤并处理事件。
	 */
	protected void handle(NotificationEvent event, Acknowledgment acknowledgment) {
		if (!processingService.matchesChannel(event, channel())) {
			acknowledgment.acknowledge();
			return;
		}
		try {
			processingService.process(event);
			acknowledgment.acknowledge();
		} catch (RuntimeException ex) {
			log.error("消费失败 channel={} notificationId={}", channel(), event.notificationId(), ex);
			throw ex;
		}
	}
}
