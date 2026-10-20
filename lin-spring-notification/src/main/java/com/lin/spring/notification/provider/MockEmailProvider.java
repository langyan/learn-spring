package com.lin.spring.notification.provider;

import com.lin.spring.notification.config.NotificationProperties;
import com.lin.spring.notification.domain.NotificationChannel;
import com.lin.spring.notification.dto.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock SendGrid Email Provider。
 */
@Slf4j
@Component
public class MockEmailProvider implements NotificationProvider {

	private final SimpleRateLimiter rateLimiter;

	public MockEmailProvider(NotificationProperties properties) {
		this.rateLimiter = new SimpleRateLimiter(properties.getProvider().getEmailTps());
	}

	@Override
	public NotificationChannel channel() {
		return NotificationChannel.EMAIL;
	}

	@Override
	public ProviderResult send(NotificationEvent event) {
		rateLimiter.acquire();
		log.info("[MockSendGrid] email sent userId={} notificationId={} templateId={}",
			event.userId(), event.notificationId(), event.templateId());
		return ProviderResult.success("mock-sendgrid");
	}
}
