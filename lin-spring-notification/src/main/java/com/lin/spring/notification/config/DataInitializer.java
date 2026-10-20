package com.lin.spring.notification.config;

import com.lin.spring.notification.domain.NotificationChannel;
import com.lin.spring.notification.entity.NotificationPreferenceEntity;
import com.lin.spring.notification.entity.NotificationTemplateEntity;
import com.lin.spring.notification.repository.NotificationPreferenceRepository;
import com.lin.spring.notification.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时初始化演示用模板与用户偏好。
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

	private final NotificationTemplateRepository templateRepository;
	private final NotificationPreferenceRepository preferenceRepository;

	@Override
	public void run(String... args) {
		if (templateRepository.count() == 0) {
			templateRepository.save(new NotificationTemplateEntity(
				"otp-sms", NotificationChannel.SMS, "zh-CN", "您的验证码是 {{code}}，{{minutes}} 分钟内有效。"));
			templateRepository.save(new NotificationTemplateEntity(
				"flash-sale-push", NotificationChannel.PUSH, "zh-CN", "闪购开始！{{productName}} 限时 {{discount}} 折。"));
			templateRepository.save(new NotificationTemplateEntity(
				"order-email", NotificationChannel.EMAIL, "zh-CN", "订单 {{orderId}} 已确认，金额 {{amount}} 元。"));
		}
		if (preferenceRepository.count() == 0) {
			preferenceRepository.save(new NotificationPreferenceEntity("user-marketing-off", NotificationChannel.EMAIL, false));
			preferenceRepository.save(new NotificationPreferenceEntity("user-marketing-off", NotificationChannel.PUSH, true));
		}
	}
}
