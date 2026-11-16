package com.lin.spring.events.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.lin.spring.events.inbox.entity.InboxStatus;
import com.lin.spring.events.inbox.repository.InboxEventRepository;
import com.lin.spring.events.order.OrderService;
import com.lin.spring.events.order.dto.CreateOrderRequest;
import com.lin.spring.events.order.repository.OrderRepository;
import com.lin.spring.events.order.repository.ProcessedOrderRepository;
import com.lin.spring.events.outbox.OutboxDispatcherScheduler;
import com.lin.spring.events.outbox.entity.OutboxStatus;
import com.lin.spring.events.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运维接口切片测试：下单入口、状态查询、链路追踪、维护总览。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpsApiTest {

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private OrderService orderService;

	@Autowired
	private OutboxDispatcherScheduler dispatcherScheduler;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private InboxEventRepository inboxEventRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private ProcessedOrderRepository processedOrderRepository;

	private String base() {
		return "http://localhost:" + port;
	}

	@BeforeEach
	void clean() {
		orderRepository.deleteAll();
		outboxEventRepository.deleteAll();
		inboxEventRepository.deleteAll();
		processedOrderRepository.deleteAll();
	}

	@Test
	@DisplayName("REST 下单 → 派发 → 运维接口可查全链路状态")
	void fullFlowVisibleThroughOpsApi() {
		CreateOrderRequest request = new CreateOrderRequest();
		request.setCustomerName("Eve");
		request.setAmount(new BigDecimal("10.00"));

		ResponseEntity<JsonNode> created = rest.postForEntity(base() + "/api/orders", request, JsonNode.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
		Long orderId = created.getBody().path("id").asLong();

		dispatcherScheduler.dispatchOnce();

		// Outbox 查询
		ResponseEntity<JsonNode> outbox = rest.getForEntity(
			base() + "/api/outbox?status=PUBLISHED", JsonNode.class);
		assertThat(outbox.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(outbox.getBody().size()).isEqualTo(1);

		// 订单投影
		ResponseEntity<JsonNode> processed = rest.getForEntity(
			base() + "/api/orders/" + orderId + "/processed", JsonNode.class);
		assertThat(processed.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(processed.getBody().size()).isEqualTo(1);

		// 链路追踪：correlationId 同时命中 Outbox 与 Inbox
		String correlationId = outbox.getBody().get(0).path("correlationId").asText();
		assertThat(correlationId).isNotBlank();
		ResponseEntity<JsonNode> trace = rest.getForEntity(
			base() + "/api/outbox/trace/" + correlationId, JsonNode.class);
		assertThat(trace.getBody().path("outbox").size()).isEqualTo(1);
		assertThat(trace.getBody().path("inbox").size()).isEqualTo(1);

		// 维护总览
		ResponseEntity<JsonNode> status = rest.getForEntity(
			base() + "/api/maintenance/status", JsonNode.class);
		assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(status.getBody().path("outbox").path("PUBLISHED").asLong()).isEqualTo(1);
		assertThat(status.getBody().path("inbox").path(InboxStatus.PROCESSED.name()).asLong()).isEqualTo(1);
	}

	@Test
	@DisplayName("重放不存在的记录返回 404")
	void retryUnknownReturns404() {
		ResponseEntity<String> resp = rest.postForEntity(
			base() + "/api/outbox/999999/retry", null, String.class);
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("健康检查包含调度器与积压详情")
	void healthExposesEventInfrastructure() {
		ResponseEntity<JsonNode> health = rest.getForEntity(
			base() + "/actuator/health/eventsInfrastructure", JsonNode.class);
		assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(health.getBody().path("details").has("outboxDispatcher")).isTrue();
		assertThat(health.getBody().path("details").has("outboxBacklog")).isTrue();
	}
}
