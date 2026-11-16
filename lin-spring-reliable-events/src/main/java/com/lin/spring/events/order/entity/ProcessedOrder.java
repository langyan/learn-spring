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
 * 消费侧投影（processed_orders）：验证「重复投递只产生一次副作用」。
 * <p>
 * eventId 唯一约束 + 消费事务写入，构成 Inbox 幂等在业务表上的最终体现。
 * </p>
 */
@Entity
@Table(name = "processed_orders")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 幂等边界：同一事件只允许投影一次。 */
	@Column(name = "event_id", nullable = false, unique = true, length = 64)
	private String eventId;

	@Column(name = "order_id", nullable = false)
	private Long orderId;

	@Column(name = "customer_name", nullable = false, length = 128)
	private String customerName;

	@Column(name = "amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal amount;

	@Column(name = "processed_at", nullable = false)
	private Instant processedAt;

	public ProcessedOrder(String eventId, Long orderId, String customerName, BigDecimal amount) {
		this.eventId = eventId;
		this.orderId = orderId;
		this.customerName = customerName;
		this.amount = amount;
		this.processedAt = Instant.now();
	}
}
