package com.lin.spring.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 投递日志，与 notifications 主表分离以减轻写入压力。
 */
@Entity
@Table(name = "delivery_logs")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryLogEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 36)
	private String notificationId;

	@Column(nullable = false, length = 32)
	private String provider;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(length = 128)
	private String errorCode;

	@Column(nullable = false)
	private Instant createdAt;

	/**
	 * 记录一次 Provider 投递结果。
	 */
	public DeliveryLogEntity(String notificationId, String provider, String status, String errorCode) {
		this.notificationId = notificationId;
		this.provider = provider;
		this.status = status;
		this.errorCode = errorCode;
		this.createdAt = Instant.now();
	}
}
