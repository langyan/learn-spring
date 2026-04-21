package com.lin.spring.outbox.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 写入 Outbox 的领域事件载荷（序列化为 JSON）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPlacedPayload {

	private String eventId;

	private String eventType;

	private Long orderId;

	private String customerName;

	private BigDecimal amount;
}
