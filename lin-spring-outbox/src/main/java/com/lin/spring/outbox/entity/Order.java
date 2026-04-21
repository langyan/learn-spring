package com.lin.spring.outbox.entity;

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
 * 订单聚合根（示例领域模型）。
 */
@Entity
@Table(name = "demo_orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "customer_name", nullable = false)
	private String customerName;

	@Column(name = "amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(name = "status", nullable = false, length = 32)
	private String status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/**
	 * 创建待保存订单。
	 *
	 * @param customerName 客户名称
	 * @param amount       金额
	 */
	public Order(String customerName, BigDecimal amount) {
		this.customerName = customerName;
		this.amount = amount;
		this.status = "CREATED";
		this.createdAt = Instant.now();
	}
}
