package com.lin.spring.events.order.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * OrderPlaced 业务载荷（封套内的 payload 字段）：人类可读 JSON。
 */
public record OrderPlacedPayload(
	Long orderId,
	String customerName,
	BigDecimal amount,
	String correlationId,
	Instant occurredAt
) {

	public static final String TYPE = "OrderPlaced";
}
