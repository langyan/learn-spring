package com.lin.spring.idempotency.service;

import com.lin.spring.idempotency.dto.CreateOrderRequest;
import com.lin.spring.idempotency.dto.OrderResponse;
import com.lin.spring.idempotency.dto.ReplayEventResponse;
import com.lin.spring.idempotency.dto.RewardRecordResponse;
import com.lin.spring.idempotency.entity.PurchaseOrder;
import com.lin.spring.idempotency.entity.RewardRecord;
import com.lin.spring.idempotency.event.OrderCreatedEvent;
import com.lin.spring.idempotency.exception.IdempotencyConflictException;
import com.lin.spring.idempotency.exception.ResourceNotFoundException;
import com.lin.spring.idempotency.repository.PurchaseOrderRepository;
import com.lin.spring.idempotency.repository.RewardRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

	private final PurchaseOrderRepository purchaseOrderRepository;
	private final RewardRecordRepository rewardRecordRepository;
	private final IdempotencyStore idempotencyStore;
	private final RequestHashService requestHashService;
	private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
	private final String orderCreatedTopic;

	public OrderService(
		PurchaseOrderRepository purchaseOrderRepository,
		RewardRecordRepository rewardRecordRepository,
		IdempotencyStore idempotencyStore,
		RequestHashService requestHashService,
		KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate,
		@Value("${app.kafka.topics.order-created}") String orderCreatedTopic
	) {
		this.purchaseOrderRepository = purchaseOrderRepository;
		this.rewardRecordRepository = rewardRecordRepository;
		this.idempotencyStore = idempotencyStore;
		this.requestHashService = requestHashService;
		this.kafkaTemplate = kafkaTemplate;
		this.orderCreatedTopic = orderCreatedTopic;
	}

	@Transactional
	public OrderActionResult createOrder(CreateOrderRequest request, String idempotencyKey) {
		String requestHash = requestHashService.hash(request);
		IdempotencyDecision decision = idempotencyStore.claim(idempotencyKey, requestHash);

		return switch (decision.type()) {
			case CLAIMED -> createFreshOrder(request, idempotencyKey, requestHash);
			case COMPLETED -> new OrderActionResult(
				HttpStatus.OK,
				decision.cachedResponse().asReplayed("Returned cached response for duplicated request")
			);
			case PROCESSING -> new OrderActionResult(
				HttpStatus.ACCEPTED,
				new OrderResponse(
					null,
					request.customerId(),
					request.productName(),
					request.quantity(),
					"PROCESSING",
					idempotencyKey,
					null,
					false,
					"Request is already being processed"
				)
			);
			case CONFLICT -> throw new IdempotencyConflictException(
				"Idempotency-Key has already been used with a different request body"
			);
		};
	}

	@Transactional(readOnly = true)
	public OrderResponse getOrder(String orderNo) {
		PurchaseOrder order = findOrder(orderNo);
		return toOrderResponse(order, false, "Fetched order successfully");
	}

	@Transactional
	public ReplayEventResponse replayEvent(String orderNo) {
		PurchaseOrder order = findOrder(orderNo);
		OrderCreatedEvent event = new OrderCreatedEvent(
			order.getEventId(),
			order.getOrderNo(),
			order.getCustomerId(),
			order.getProductName(),
			order.getQuantity(),
			order.getCreatedAt()
		);
		kafkaTemplate.send(orderCreatedTopic, order.getOrderNo(), event);
		return new ReplayEventResponse(order.getOrderNo(), order.getEventId(), "Replayed the same Kafka event for dedupe testing");
	}

	@Transactional(readOnly = true)
	public List<RewardRecordResponse> getRewardRecords(String orderNo) {
		findOrder(orderNo);
		return rewardRecordRepository.findByOrderNoOrderByProcessedAtAsc(orderNo).stream()
			.map(this::toRewardRecordResponse)
			.toList();
	}

	private OrderActionResult createFreshOrder(CreateOrderRequest request, String idempotencyKey, String requestHash) {
		try {
			PurchaseOrder order = new PurchaseOrder();
			order.setOrderNo("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
			order.setCustomerId(request.customerId());
			order.setProductName(request.productName());
			order.setQuantity(request.quantity());
			order.setOrderStatus("CREATED");
			order.setIdempotencyKey(idempotencyKey);
			order.setEventId("evt-" + UUID.randomUUID());
			order.setCreatedAt(Instant.now());

			PurchaseOrder savedOrder = purchaseOrderRepository.save(order);
			OrderCreatedEvent event = new OrderCreatedEvent(
				savedOrder.getEventId(),
				savedOrder.getOrderNo(),
				savedOrder.getCustomerId(),
				savedOrder.getProductName(),
				savedOrder.getQuantity(),
				savedOrder.getCreatedAt()
			);
			kafkaTemplate.send(orderCreatedTopic, savedOrder.getOrderNo(), event);

			OrderResponse response = toOrderResponse(savedOrder, false, "Order created and event published");
			idempotencyStore.markCompleted(idempotencyKey, requestHash, response);
			return new OrderActionResult(HttpStatus.CREATED, response);
		} catch (RuntimeException ex) {
			idempotencyStore.releaseProcessing(idempotencyKey);
			throw ex;
		}
	}

	private PurchaseOrder findOrder(String orderNo) {
		return purchaseOrderRepository.findByOrderNo(orderNo)
			.orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNo));
	}

	private OrderResponse toOrderResponse(PurchaseOrder order, boolean replayed, String message) {
		return new OrderResponse(
			order.getOrderNo(),
			order.getCustomerId(),
			order.getProductName(),
			order.getQuantity(),
			order.getOrderStatus(),
			order.getIdempotencyKey(),
			order.getEventId(),
			replayed,
			message
		);
	}

	private RewardRecordResponse toRewardRecordResponse(RewardRecord record) {
		return new RewardRecordResponse(
			record.getEventId(),
			record.getOrderNo(),
			record.getAction(),
			record.getNote(),
			record.getProcessedAt()
		);
	}
}
