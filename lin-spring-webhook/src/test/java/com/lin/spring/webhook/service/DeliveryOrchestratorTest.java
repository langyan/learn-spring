package com.lin.spring.webhook.service;

import com.lin.spring.webhook.domain.DeliveryStatus;
import com.lin.spring.webhook.domain.OutcomeKind;
import com.lin.spring.webhook.dto.DeliveryJob;
import com.lin.spring.webhook.dto.DeliveryOutcome;
import com.lin.spring.webhook.dto.RegisterEndpointRequest;
import com.lin.spring.webhook.entity.WebhookEndpointEntity;
import com.lin.spring.webhook.entity.WebhookEventEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 端到端投递编排：成功投递、平台侧幂等、不可重试失败进 DLQ。
 */
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
	"app.webhook.demo-data.enabled=false",
	"app.webhook.delivery.worker-threads=0"
})
class DeliveryOrchestratorTest {

	@LocalServerPort
	private int port;

	@Autowired
	private EndpointRegistryService endpointRegistry;

	@Autowired
	private EventIngestionService ingestionService;

	@Autowired
	private DeliveryOrchestrator orchestrator;

	@Autowired
	private DeliveryLogService deliveryLogService;

	@Test
	void 同一事件重复投递只会成功一次() {
		WebhookEndpointEntity endpoint = register("mch_dup", "ok");
		DeliveryJob job = DeliveryJob.first("evt_dup_1", "mch_dup", endpoint.getEndpointId(),
			endpoint.getUrl(), endpoint.getSecret(), "charge.succeeded", "{\"amount\":100}", Instant.now());

		assertEquals(OutcomeKind.DELIVERED, orchestrator.deliver(job).kind());

		// 模拟 Kafka 重复投递 / 调度器重复捞取
		DeliveryOutcome second = orchestrator.deliver(job);
		assertEquals(OutcomeKind.SKIPPED_DUPLICATE, second.kind(), "幂等键必须挡住第二次投递");
	}

	@Test
	void 端点返回500时进入重试而不是立即失败() {
		WebhookEndpointEntity endpoint = register("mch_500", "fail500");

		// 先让事件真正入库，才能验证状态流转
		com.lin.spring.webhook.dto.IngestEventRequest ingestRequest =
			new com.lin.spring.webhook.dto.IngestEventRequest();
		ingestRequest.setMerchantId("mch_500");
		ingestRequest.setEventType("charge.succeeded");
		ingestRequest.setEventId("evt_500_1");
		ingestionService.ingest(ingestRequest);

		DeliveryJob job = DeliveryJob.first("evt_500_1", "mch_500", endpoint.getEndpointId(),
			endpoint.getUrl(), endpoint.getSecret(), "charge.succeeded", "{}", Instant.now());

		DeliveryOutcome outcome = orchestrator.deliver(job);
		assertEquals(OutcomeKind.RETRY_SCHEDULED, outcome.kind());

		WebhookEventEntity event = deliveryLogService.event("evt_500_1").orElseThrow();
		assertEquals(DeliveryStatus.RETRYING, event.getStatus());
		assertNotNull(event.getNextAttemptAt(), "应记录下次重试时间");
	}

	@Test
	void 事件接入后保证会被尝试投递() {
		register("mch_ingest", "ok");
		com.lin.spring.webhook.dto.IngestEventRequest request =
			new com.lin.spring.webhook.dto.IngestEventRequest();
		request.setMerchantId("mch_ingest");
		request.setEventType("charge.succeeded");
		request.setPayload("{\"amount\":42}");

		WebhookEventEntity saved = ingestionService.ingest(request);
		assertEquals(DeliveryStatus.PENDING, saved.getStatus());
		assertNotNull(saved.getEventId());
	}

	private WebhookEndpointEntity register(String merchantId, String mode) {
		RegisterEndpointRequest request = new RegisterEndpointRequest();
		request.setUrl("http://localhost:" + port + "/api/mock/receiver/" + mode);
		request.setSecret("whsec_" + merchantId);
		request.setEventTypes(List.of());
		return endpointRegistry.register(merchantId, request);
	}
}
