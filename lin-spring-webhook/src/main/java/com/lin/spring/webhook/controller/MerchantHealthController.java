package com.lin.spring.webhook.controller;

import com.lin.spring.webhook.entity.WebhookEndpointEntity;
import com.lin.spring.webhook.isolation.DestinationCircuitBreakerRegistry;
import com.lin.spring.webhook.isolation.MerchantLaneRouter;
import com.lin.spring.webhook.isolation.PerMerchantConcurrencyGuard;
import com.lin.spring.webhook.queue.DeliveryQueue;
import com.lin.spring.webhook.service.EndpointRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商户健康度视图：直观看到「噪声邻居」被隔离到哪条泳道。
 */
@RestController
@RequestMapping("/api/merchants/{merchantId}/health")
@RequiredArgsConstructor
public class MerchantHealthController {

	private final EndpointRegistryService endpointRegistry;
	private final MerchantLaneRouter laneRouter;
	private final DestinationCircuitBreakerRegistry breakerRegistry;
	private final PerMerchantConcurrencyGuard concurrencyGuard;
	private final DeliveryQueue queue;

	@GetMapping
	public Map<String, Object> health(@PathVariable String merchantId) {
		List<WebhookEndpointEntity> endpoints = endpointRegistry.allEndpoints(merchantId);
		Map<String, Object> lanes = new LinkedHashMap<>();
		for (WebhookEndpointEntity endpoint : endpoints) {
			String lane = laneRouter.route(endpoint.getEndpointId()).name();
			lanes.put(endpoint.getEndpointId(), Map.of(
				"url", endpoint.getUrl(),
				"status", endpoint.getStatus().name(),
				"lane", lane,
				"circuit", breakerRegistry.forDestination(endpoint.getEndpointId()).getState().name()));
		}
		return Map.of(
			"merchantId", merchantId,
			"concurrencyPermits", concurrencyGuard.permits(),
			"inFlight", concurrencyGuard.inFlight(merchantId),
			"endpoints", lanes);
	}

	/**
	 * 全局视图：所有断路器的状态与队列深度。
	 */
	@GetMapping("/global")
	public Map<String, Object> global() {
		return Map.of(
			"circuits", breakerRegistry.snapshot(),
			"readyDepth", queue.readyDepth(),
			"delayedDepth", queue.delayedDepth());
	}
}
