package com.lin.spring.notification.provider;

import com.lin.spring.notification.domain.NotificationChannel;
import com.lin.spring.notification.dto.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock 应用内通知 Provider（写入日志模拟）。
 */
@Slf4j
@Component
public class MockInAppProvider implements NotificationProvider {

	@Override
	public NotificationChannel channel() {
		return NotificationChannel.IN_APP;
	}

	@Override
	public ProviderResult send(NotificationEvent event) {
		log.info("[MockInApp] in-app notification userId={} notificationId={}",
			event.userId(), event.notificationId());
		return ProviderResult.success("mock-in-app");
	}
}
