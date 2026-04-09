package com.lin.spring.idempotency.controller;

import com.lin.spring.idempotency.dto.CreateOrderRequest;
import com.lin.spring.idempotency.dto.OrderResponse;
import com.lin.spring.idempotency.dto.ReplayEventResponse;
import com.lin.spring.idempotency.dto.RewardRecordResponse;
import com.lin.spring.idempotency.service.OrderActionResult;
import com.lin.spring.idempotency.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping
	public ResponseEntity<OrderResponse> createOrder(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@Valid @RequestBody CreateOrderRequest request
	) {
		OrderActionResult result = orderService.createOrder(request, idempotencyKey);
		return ResponseEntity.status(result.httpStatus()).body(result.response());
	}

	@GetMapping("/{orderNo}")
	public OrderResponse getOrder(@PathVariable String orderNo) {
		return orderService.getOrder(orderNo);
	}

	@PostMapping("/{orderNo}/events/replay")
	public ReplayEventResponse replayEvent(@PathVariable String orderNo) {
		return orderService.replayEvent(orderNo);
	}

	@GetMapping("/{orderNo}/reward-records")
	public List<RewardRecordResponse> getRewardRecords(@PathVariable String orderNo) {
		return orderService.getRewardRecords(orderNo);
	}
}
