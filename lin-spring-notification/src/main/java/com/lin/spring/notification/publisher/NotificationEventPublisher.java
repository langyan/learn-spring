package com.lin.spring.notification.publisher;

import com.lin.spring.notification.dto.NotificationEvent;

/**
 * 通知事件发布抽象（Kafka 或进程内）。
 */
public interface NotificationEventPublisher {

	/**
	 * 发布通知事件。
	 *
	 * @param topic Kafka Topic（进程内模式可忽略）
	 * @param event 事件载荷
	 */
	void publish(String topic, NotificationEvent event);
}
