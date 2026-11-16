package com.lin.spring.events.order;

import com.lin.spring.events.order.dto.CreateOrderRequest;
import com.lin.spring.events.order.entity.Order;
import com.lin.spring.events.order.entity.ProcessedOrder;
import com.lin.spring.events.order.repository.ProcessedOrderRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 订单入口：创建订单（同事务写 Outbox）与查询业务/投影状态。
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

	private final OrderService orderService;

	private final ProcessedOrderRepository processedOrderRepository;

	/**
	 * 创建订单：业务写库与 Outbox 事件同一事务提交。
	 */
	@PostMapping
	public ResponseEntity<Order> create(@Valid @RequestBody CreateOrderRequest request) {
		Order order = orderService.createOrder(request);
		return ResponseEntity.ok(order);
	}

	/**
	 * 查询订单。
	 */
	@GetMapping("/{id}")
	public ResponseEntity<Order> get(@PathVariable Long id) {
		return ResponseEntity.ok(orderService.getOrder(id));
	}

	/**
	 * 查询消费侧投影：验证事件是否已被下游处理（幂等：每个 eventId 只有一条）。
	 */
	@GetMapping("/{id}/processed")
	public ResponseEntity<List<ProcessedOrder>> processed(@PathVariable Long id) {
		List<ProcessedOrder> projections = processedOrderRepository.findByOrderId(id);
		if (projections.isEmpty()) {
			throw new ResponseStatusException(NOT_FOUND, "该订单尚无消费侧投影: " + id);
		}
		return ResponseEntity.ok(projections);
	}
}
