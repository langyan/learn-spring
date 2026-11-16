package com.lin.spring.events.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.spring.events.outbox.entity.OutboxEvent;
import com.lin.spring.events.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox 追加器：在<b>调用方事务内</b>写入事件记录。
 * <p>
 * 故意不加 @Transactional：必须与业务写库共享同一事务边界，
 * 由业务服务（如 OrderService）的 @Transactional 统一提交或回滚，这正是 Outbox 模式的核心。
 * </p>
 * <p>
 * 载荷为人类可读的 JSON「封套」：eventId/eventType/correlationId 等元数据与业务字段平铺，
 * 消费侧无需额外协议即可提取幂等边界。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxAppender {

	private final OutboxEventRepository outboxEventRepository;

	private final ObjectMapper objectMapper;

	/**
	 * 在当前事务中追加一条待派发事件。
	 *
	 * @param eventType     事件类型（如 OrderPlaced）
	 * @param aggregateType 聚合类型（如 Order）
	 * @param aggregateId   聚合 ID
	 * @param source        来源服务名
	 * @param correlationId 业务链路关联 ID（可为 null）
	 * @param payload       业务载荷对象（封套内的 payload 字段）
	 * @return 已持久化的事件
	 */
	public OutboxEvent append(String eventType, String aggregateType, String aggregateId,
							  String source, String correlationId, Object payload) {
		String eventId = UUID.randomUUID().toString().replace("-", "");
		Map<String, Object> envelope = new LinkedHashMap<>();
		envelope.put("eventId", eventId);
		envelope.put("eventType", eventType);
		envelope.put("aggregateType", aggregateType);
		envelope.put("aggregateId", aggregateId);
		envelope.put("source", source);
		envelope.put("correlationId", correlationId);
		envelope.put("occurredAt", Instant.now().toString());
		envelope.put("payload", payload);

		String json;
		try {
			json = objectMapper.writeValueAsString(envelope);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("序列化 Outbox 载荷失败: " + eventType, e);
		}
		OutboxEvent event = new OutboxEvent(eventId, eventType, aggregateType, aggregateId,
			source, correlationId, json);
		OutboxEvent saved = outboxEventRepository.save(event);
		log.debug("Outbox appended: eventId={} type={} aggregate={} correlationId={}",
			eventId, eventType, aggregateId, correlationId);
		return saved;
	}
}
