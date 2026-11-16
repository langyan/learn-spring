package com.lin.spring.events.inbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.spring.events.inbox.entity.InboxEvent;
import com.lin.spring.events.ops.FailureSimulator;
import com.lin.spring.events.order.dto.OrderPlacedPayload;
import com.lin.spring.events.order.entity.ProcessedOrder;
import com.lin.spring.events.order.repository.ProcessedOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 演示处理器：消费 OrderPlaced，写消费侧投影 processed_orders。
 * <p>
 * eventId 唯一约束 + 事务写入 → 即使事件被重复投递/重复处理，投影也只产生一次；
 * 处理失败（可注入）抛异常，由 Inbox 重试编排接管。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPlacedInboxHandler implements InboxHandler {

	private final ProcessedOrderRepository processedOrderRepository;

	private final ObjectMapper objectMapper;

	private final FailureSimulator failureSimulator;

	@Override
	public String eventType() {
		return OrderPlacedPayload.TYPE;
	}

	@Override
	public void handle(InboxEvent event) throws Exception {
		failureSimulator.maybeFailHandler(event.getEventId());

		JsonNode root = objectMapper.readTree(event.getPayload());
		JsonNode payload = root.path("payload");
		OrderPlacedPayload placed = objectMapper.treeToValue(payload, OrderPlacedPayload.class);

		// 幂等兜底：已投影过的事件直接跳过（正常情况下 Inbox 已去重，此处防御 PROCESSING 崩溃重放）
		if (processedOrderRepository.findByEventId(event.getEventId()).isPresent()) {
			log.info("Projection already exists, skip: eventId={}", event.getEventId());
			return;
		}
		processedOrderRepository.save(new ProcessedOrder(
			event.getEventId(),
			placed.orderId(),
			placed.customerName(),
			placed.amount()
		));
		log.info("OrderPlaced processed: eventId={} orderId={} customer={} amount={}",
			event.getEventId(), placed.orderId(), placed.customerName(), placed.amount());
	}
}
