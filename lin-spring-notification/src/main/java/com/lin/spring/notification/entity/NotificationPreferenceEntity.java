package com.lin.spring.notification.entity;

import com.lin.spring.notification.domain.NotificationChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户通知偏好（按渠道开关）。
 */
@Entity
@Table(
	name = "notification_preferences",
	uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "channel"})
)
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreferenceEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, length = 64)
	private String userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationChannel channel;

	@Column(nullable = false)
	private boolean enabled = true;

	/**
	 * 构造用户渠道偏好。
	 */
	public NotificationPreferenceEntity(String userId, NotificationChannel channel, boolean enabled) {
		this.userId = userId;
		this.channel = channel;
		this.enabled = enabled;
	}
}
