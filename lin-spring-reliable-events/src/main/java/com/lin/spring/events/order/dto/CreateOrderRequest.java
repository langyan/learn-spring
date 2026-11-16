package com.lin.spring.events.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建订单请求。
 */
@Data
public class CreateOrderRequest {

	@NotBlank
	private String customerName;

	@Positive
	private BigDecimal amount;
}
