package com.lin.spring.webhook.dispatcher;

import com.lin.spring.webhook.domain.OutcomeKind;
import com.lin.spring.webhook.dto.DeliveryJob;
import com.lin.spring.webhook.dto.DeliveryOutcome;
import com.lin.spring.webhook.dto.RegisterEndpointRequest;
import com.lin.spring.webhook.entity.WebhookEndpointEntity;
import com.lin.spring.webhook.service.DeliveryOrchestrator;
import com.lin.spring.webhook.service.EndpointRegistryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 噪声邻居（追问 2）：一个「接受连接后挂起 30 秒」的慢商户，不能拖垮健康商户。
 *
 * <p>测试把每商户并发配额压到 1、响应超时压到 600ms，让「配额保护」与「超时保护」都能在秒级内被观察到。</p>
 */
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
	"app.webhook.demo-data.enabled=false",
	"app.webhook.delivery.worker-threads=0",
	"app.webhook.isolation.per-merchant-max-concurrency=1",
	"app.webhook.isolation.defer-seconds=1",
	"app.webhook.delivery.connect-timeout-ms=300",
	"app.webhook.delivery.response-timeout-ms=600"
})
class NoisyNeighborTest {

	@LocalServerPort
	private int port;

	@Autowired
	private EndpointRegistryService endpointRegistry;

	@Autowired
	private DeliveryOrchestrator orchestrator;

	@Test
	void 慢商户无法占满投递资源健康商户不受影响() throws Exception {
		WebhookEndpointEntity noisy = register("mch_noisy", "hang30s");
		WebhookEndpointEntity calm = register("mch_calm", "ok");

		int noisyJobs = 6;
		ExecutorService pool = Executors.newFixedThreadPool(12);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<DeliveryOutcome>> noisyFutures = new ArrayList<>();
		for (int i = 0; i < noisyJobs; i++) {
			String eventId = "evt_noisy_" + i;
			noisyFutures.add(pool.submit(() -> {
				start.await();
				return orchestrator.deliver(job(eventId, "mch_noisy", noisy));
			}));
		}
		Future<DeliveryOutcome> calmFuture = pool.submit(() -> {
			start.await();
			return orchestrator.deliver(job("evt_calm_1", "mch_calm", calm));
		});

		start.countDown();

		long calmStart = System.currentTimeMillis();
		DeliveryOutcome calmOutcome = calmFuture.get(20, TimeUnit.SECONDS);
		long calmElapsed = System.currentTimeMillis() - calmStart;

		// 关键断言：健康商户的密码重置 webhook 没有被慢商户堵住
		assertEquals(OutcomeKind.DELIVERED, calmOutcome.kind());
		assertTrue(calmElapsed < 5_000, "健康商户不应被慢商户拖住，实际耗时 " + calmElapsed + "ms");

		long deferred = 0;
		for (Future<DeliveryOutcome> future : noisyFutures) {
			if (future.get(30, TimeUnit.SECONDS).kind() == OutcomeKind.DEFERRED) {
				deferred++;
			}
		}
		assertTrue(deferred >= noisyJobs - 2,
			"慢商户的超额请求应被租户配额挡下并转入延迟队列，实际 deferred=" + deferred);

		pool.shutdownNow();
	}

	private WebhookEndpointEntity register(String merchantId, String mode) {
		RegisterEndpointRequest request = new RegisterEndpointRequest();
		request.setUrl("http://localhost:" + port + "/api/mock/receiver/" + mode);
		request.setSecret("whsec_" + merchantId);
		request.setEventTypes(List.of());
		return endpointRegistry.register(merchantId, request);
	}

	private DeliveryJob job(String eventId, String merchantId, WebhookEndpointEntity endpoint) {
		return DeliveryJob.first(eventId, merchantId, endpoint.getEndpointId(), endpoint.getUrl(),
			endpoint.getSecret(), "charge.succeeded", "{\"amount\":1000}", Instant.now());
	}
}
