package com.lin.spring.notification.provider;

import com.lin.spring.notification.config.NotificationProperties;
import com.lin.spring.notification.domain.NotificationChannel;
import com.lin.spring.notification.dto.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock FCM Push Provider，演示限流与错误分类。
 */
@Slf4j
@Component
public class MockPushProvider implements NotificationProvider {

	private final SimpleRateLimiter rateLimiter;

	public MockPushProvider(NotificationProperties properties) {
		this.rateLimiter = new SimpleRateLimiter(properties.getProvider().getPushTps());
	}

	@Override
	public NotificationChannel channel() {
		return NotificationChannel.PUSH;
	}

	@Override
	public ProviderResult send(NotificationEvent event) {
		rateLimiter.acquire();
		Map<String, Object> data = event.data();
		if (data != null && "INVALID_TOKEN".equals(data.get("simulateError"))) {
			return ProviderResult.failedNonRetryable("mock-fcm", "UNREGISTERED");
		}
		if (data != null && "TRANSIENT".equals(data.get("simulateError"))) {
			return ProviderResult.failedRetryable("mock-fcm", "SERVICE_UNAVAILABLE");
		}
		if (ThreadLocalRandom.current().nextInt(1000) == 0) {
			return ProviderResult.failedRetryable("mock-fcm", "RANDOM_TIMEOUT");
		}
		log.info("[MockFCM] push sent userId={} notificationId={} correlationId={}",
			event.userId(), event.notificationId(), event.correlationId());
		return ProviderResult.success("mock-fcm");
	}
}
