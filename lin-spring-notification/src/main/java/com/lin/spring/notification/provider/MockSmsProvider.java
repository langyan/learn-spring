package com.lin.spring.notification.provider;

import com.lin.spring.notification.config.NotificationProperties;
import com.lin.spring.notification.domain.NotificationChannel;
import com.lin.spring.notification.dto.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Mock Twilio SMS Provider。
 */
@Slf4j
@Component
public class MockSmsProvider implements NotificationProvider {

	private final SimpleRateLimiter rateLimiter;

	public MockSmsProvider(NotificationProperties properties) {
		this.rateLimiter = new SimpleRateLimiter(properties.getProvider().getSmsTps());
	}

	@Override
	public NotificationChannel channel() {
		return NotificationChannel.SMS;
	}

	@Override
	public ProviderResult send(NotificationEvent event) {
		rateLimiter.acquire();
		Map<String, Object> data = event.data();
		if (data != null && "INVALID_PHONE".equals(data.get("simulateError"))) {
			return ProviderResult.failedNonRetryable("mock-twilio", "INVALID_PHONE");
		}
		log.info("[MockTwilio] sms sent userId={} notificationId={} correlationId={}",
			event.userId(), event.notificationId(), event.correlationId());
		return ProviderResult.success("mock-twilio");
	}
}
