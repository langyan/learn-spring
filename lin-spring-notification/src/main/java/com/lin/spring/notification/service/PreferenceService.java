package com.lin.spring.notification.service;

import com.lin.spring.notification.domain.NotificationChannel;
import com.lin.spring.notification.domain.NotificationType;
import com.lin.spring.notification.entity.NotificationPreferenceEntity;
import com.lin.spring.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户通知偏好校验服务。
 */
@Service
@RequiredArgsConstructor
public class PreferenceService {

	private final NotificationPreferenceRepository preferenceRepository;

	/**
	 * 判断用户是否允许接收该渠道通知。
	 * <p>
	 * OTP / SECURITY 不受偏好限制；无偏好记录时默认允许。
	 * </p>
	 *
	 * @param userId  用户 ID
	 * @param type    通知类型
	 * @param channel 渠道
	 * @return true 表示允许发送
	 */
	public boolean isAllowed(String userId, NotificationType type, NotificationChannel channel) {
		if (type == NotificationType.OTP || type == NotificationType.SECURITY) {
			return true;
		}
		return preferenceRepository.findByUserIdAndChannel(userId, channel)
			.map(NotificationPreferenceEntity::isEnabled)
			.orElse(true);
	}
}
