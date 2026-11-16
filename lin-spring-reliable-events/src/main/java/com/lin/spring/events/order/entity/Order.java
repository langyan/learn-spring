package com.lin.spring.events.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 订单聚合：Outbox 事件的生产方业务数据。
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "customer_name", nullable = false, length = 128)
	private String customerName;

	@Column(name = "amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	/** 业务链路关联 ID：与 Outbox/Inbox 记录一致，供端到端排查。 */
	@Column(name = "correlation_id", nullable = false, length = 64)
	private String correlationId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public Order(String customerName, BigDecimal amount, String correlationId) {
		this.customerName = customerName;
		this.amount = amount;
		this.correlationId = correlationId;
		this.createdAt = Instant.now();
	}
}
