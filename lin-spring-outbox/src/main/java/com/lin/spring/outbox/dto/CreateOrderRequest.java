package com.lin.spring.outbox.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建订单请求体。
 */
@Data
public class CreateOrderRequest {

	@NotBlank
	private String customerName;

	@NotNull
	@DecimalMin(value = "0.01", inclusive = true)
	private BigDecimal amount;
}
