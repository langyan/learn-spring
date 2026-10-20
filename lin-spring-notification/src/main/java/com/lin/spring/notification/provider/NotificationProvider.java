package com.lin.spring.notification.provider;

import com.lin.spring.notification.domain.NotificationChannel;
import com.lin.spring.notification.dto.NotificationEvent;

/**
 * 外部通知 Provider 抽象（FCM / SendGrid / Twilio 等）。
 */
public interface NotificationProvider {

	/**
	 * 该 Provider 负责的渠道。
	 */
	NotificationChannel channel();

	/**
	 * 向外部 Provider 发送通知。
	 *
	 * @param event 通知事件
	 * @return 投递结果
	 */
	ProviderResult send(NotificationEvent event);
}
