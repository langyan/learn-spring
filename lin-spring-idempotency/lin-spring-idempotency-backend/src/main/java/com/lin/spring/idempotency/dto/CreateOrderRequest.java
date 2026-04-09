package com.lin.spring.idempotency.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(
	@NotBlank(message = "customerId must not be blank")
	String customerId,
	@NotBlank(message = "productName must not be blank")
	String productName,
	@Min(value = 1, message = "quantity must be at least 1")
	int quantity
) {
}
