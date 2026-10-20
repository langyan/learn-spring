package com.lin.spring.notification.repository;

import com.lin.spring.notification.domain.NotificationChannel;
import com.lin.spring.notification.entity.NotificationPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户通知偏好数据访问。
 */
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreferenceEntity, Long> {

	Optional<NotificationPreferenceEntity> findByUserIdAndChannel(String userId, NotificationChannel channel);
}
