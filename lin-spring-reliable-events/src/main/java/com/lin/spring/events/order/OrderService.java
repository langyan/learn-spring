package com.lin.spring.events.order;

import com.lin.spring.events.order.dto.CreateOrderRequest;
import com.lin.spring.events.order.dto.OrderPlacedPayload;
import com.lin.spring.events.order.entity.Order;
import com.lin.spring.events.order.repository.OrderRepository;
import com.lin.spring.events.outbox.OutboxAppender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 订单应用服务：业务写库与 Outbox 事件在同一本地事务中提交，杜绝双写不一致。
 * <p>
 * 与「先 save 再 kafkaTemplate.send」的朴素做法对比：
 * 此处事务提交时事件必然已落库；broker 投递由派发器异步完成，失败可重试、可排查。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

	public static final String AGGREGATE_TYPE = "Order";

	private final OrderRepository orderRepository;

	private final OutboxAppender outboxAppender;

	@Value("${spring.application.name:lin-spring-reliable-events}")
	private String source;

	/**
	 * 创建订单并写入 Outbox 事件（同一事务）。
	 */
	@Transactional
	public Order createOrder(CreateOrderRequest request) {
		String correlationId = UUID.randomUUID().toString().replace("-", "");
		return createOrderWithCorrelation(request, correlationId);
	}

	/**
	 * 指定 correlationId 创建（供链路演示与测试复用同一链路 ID）。
	 */
	@Transactional
	public Order createOrderWithCorrelation(CreateOrderRequest request, String correlationId) {
		Order order = new Order(request.getCustomerName(), request.getAmount(), correlationId);
		Order saved = orderRepository.save(order);

		outboxAppender.append(
			OrderPlacedPayload.TYPE,
			AGGREGATE_TYPE,
			String.valueOf(saved.getId()),
			source,
			correlationId,
			new OrderPlacedPayload(saved.getId(), saved.getCustomerName(),
				saved.getAmount(), correlationId, Instant.now())
		);
		log.info("Order created with outbox event: orderId={} correlationId={}",
			saved.getId(), correlationId);
		return saved;
	}

	/**
	 * 仅用于集成测试：同事务写入订单与事件后抛异常，验证两者一并回滚。
	 */
	@Transactional
	public Order createOrderThenFailForTest(CreateOrderRequest request) {
		Order order = createOrder(request);
		throw new IllegalStateException("simulated business failure after persist");
	}

	/**
	 * 按主键查询订单。
	 */
	public Order getOrder(Long id) {
		return orderRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "订单不存在: " + id));
	}
}
