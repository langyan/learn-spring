package com.lin.spring.outbox.controller;

import com.lin.spring.outbox.dto.CreateOrderRequest;
import com.lin.spring.outbox.entity.Order;
import com.lin.spring.outbox.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单 REST API。
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

	private final OrderService orderService;

	/**
	 * 创建订单（同事务写入 Outbox）。
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Order create(@Valid @RequestBody CreateOrderRequest request) {
		return orderService.createOrder(request);
	}

	/**
	 * 按 ID 查询订单（演示用）。
	 */
	@GetMapping("/{id}")
	public Order getById(@PathVariable Long id) {
		return orderService.getOrder(id);
	}
}
