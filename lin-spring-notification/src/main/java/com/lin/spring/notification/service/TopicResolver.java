package com.lin.spring.notification.service;

import com.lin.spring.notification.config.NotificationProperties;
import com.lin.spring.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

/**
 * 根据通知类型解析 Kafka Topic（优先级隔离）。
 */
@Component
public class TopicResolver {

	private final NotificationProperties properties;

	public TopicResolver(NotificationProperties properties) {
		this.properties = properties;
	}

	/**
	 * 解析目标 Topic 名称。
	 *
	 * @param type 通知类型
	 * @return Topic 名称
	 */
	public String resolve(NotificationType type) {
		return switch (type) {
			case OTP, SECURITY -> properties.getTopics().getCritical();
			case TRANSACTIONAL -> properties.getTopics().getTransactional();
			case MARKETING -> properties.getTopics().getMarketing();
		};
	}
}
