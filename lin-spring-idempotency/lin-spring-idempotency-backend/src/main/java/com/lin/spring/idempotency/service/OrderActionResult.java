package com.lin.spring.idempotency.service;

import com.lin.spring.idempotency.dto.OrderResponse;
import org.springframework.http.HttpStatus;

public record OrderActionResult(
	HttpStatus httpStatus,
	OrderResponse response
) {
}
