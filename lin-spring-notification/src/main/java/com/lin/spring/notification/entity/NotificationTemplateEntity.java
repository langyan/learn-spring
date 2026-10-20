package com.lin.spring.notification.entity;

import com.lin.spring.notification.domain.NotificationChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 通知模板实体。
 */
@Entity
@Table(name = "notification_templates")
@Getter
@Setter
@NoArgsConstructor
public class NotificationTemplateEntity {

	@Id
	@Column(length = 64)
	private String id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationChannel channel;

	@Column(nullable = false, length = 8)
	private String locale;

	@Lob
	@Column(nullable = false, columnDefinition = "CLOB")
	private String body;

	/**
	 * 构造通知模板。
	 */
	public NotificationTemplateEntity(String id, NotificationChannel channel, String locale, String body) {
		this.id = id;
		this.channel = channel;
		this.locale = locale;
		this.body = body;
	}
}
