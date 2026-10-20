package com.lin.spring.notification.service;

import com.lin.spring.notification.domain.NotificationChannel;
import com.lin.spring.notification.domain.NotificationStatus;
import com.lin.spring.notification.domain.NotificationType;
import com.lin.spring.notification.dto.NotificationRequest;
import com.lin.spring.notification.dto.NotificationResponse;
import com.lin.spring.notification.entity.NotificationEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 通知服务集成测试（本地进程内模式）。
 */
@SpringBootTest
class NotificationServiceTest {

	@Autowired
	private NotificationService notificationService;

	/**
	 * 验证 OTP 通知异步投递后状态变为 SENT。
	 */
	@Test
	void sendOtpNotification_asyncDeliveryMarksSent() {
		NotificationResponse response = notificationService.send(new NotificationRequest(
			"test-user-otp",
			NotificationType.OTP,
			NotificationChannel.SMS,
			"otp-sms",
			Map.of("code", "123456", "minutes", 5),
			"test-idem-" + System.nanoTime(),
			null
		));

		assertThat(response.status()).isEqualTo(NotificationStatus.PENDING);

		await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
			NotificationEntity entity = notificationService.getById(response.notificationId());
			assertThat(entity.getStatus()).isEqualTo(NotificationStatus.SENT);
		});
	}

	/**
	 * 验证用户关闭营销邮件偏好时通知被 BLOCKED。
	 */
	@Test
	void sendMarketingEmail_whenPreferenceDisabled_blocksNotification() {
		NotificationResponse response = notificationService.send(new NotificationRequest(
			"user-marketing-off",
			NotificationType.MARKETING,
			NotificationChannel.EMAIL,
			"order-email",
			Map.of("orderId", "X1", "amount", 10),
			"blocked-test-" + System.nanoTime(),
			null
		));

		assertThat(response.status()).isEqualTo(NotificationStatus.BLOCKED);
	}
}
