package com.lin.spring.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.spring.outbox.dto.CreateOrderRequest;
import com.lin.spring.outbox.dto.OrderPlacedPayload;
import com.lin.spring.outbox.entity.Order;
import com.lin.spring.outbox.entity.OutboxMessage;
import com.lin.spring.outbox.repository.OrderRepository;
import com.lin.spring.outbox.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 订单应用服务。
 * <p>
 * 与「先提交数据库再发 Kafka」的双写不同，此处将订单与 {@link OutboxMessage} 放在同一
 * {@link Transactional} 方法中持久化：要么一起提交，要么一起回滚，避免库已提交但消息丢失。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class OrderService {

	private static final String AGGREGATE_TYPE = "Order";

	private static final String EVENT_TYPE_ORDER_PLACED = "OrderPlaced";

	private final OrderRepository orderRepository;

	private final OutboxMessageRepository outboxMessageRepository;

	private final ObjectMapper objectMapper;

	/**
	 * 创建订单并写入 Outbox（同一本地事务）。
	 *
	 * @param request 请求参数
	 * @return 已持久化订单
	 */
	@Transactional
	public Order createOrder(CreateOrderRequest request) {
		Order order = new Order(request.getCustomerName(), request.getAmount());
		Order saved = orderRepository.save(order);

		String eventId = UUID.randomUUID().toString().replace("-", "");
		OrderPlacedPayload payload = new OrderPlacedPayload(
			eventId,
			EVENT_TYPE_ORDER_PLACED,
			saved.getId(),
			saved.getCustomerName(),
			saved.getAmount()
		);
		String json;
		try {
			json = objectMapper.writeValueAsString(payload);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("序列化 OrderPlaced 载荷失败", e);
		}

		OutboxMessage outbox = new OutboxMessage(
			eventId,
			AGGREGATE_TYPE,
			String.valueOf(saved.getId()),
			EVENT_TYPE_ORDER_PLACED,
			json
		);
		outboxMessageRepository.save(outbox);
		return saved;
	}

	/**
	 * 仅用于集成测试：同事务写入订单与 Outbox 后抛出异常，验证数据一并回滚。
	 *
	 * @param request 请求参数
	 */
	@Transactional
	public void createOrderThenFailForTest(CreateOrderRequest request) {
		createOrder(request);
		throw new IllegalStateException("simulated business failure after persist");
	}

	/**
	 * 按主键查询订单。
	 *
	 * @param id 订单 ID
	 * @return 订单实体
	 */
	public Order getOrder(Long id) {
		return orderRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "订单不存在: " + id));
	}
}
