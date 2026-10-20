package com.lin.spring.webhook.isolation;

import com.lin.spring.webhook.config.WebhookProperties;
import com.lin.spring.webhook.domain.MerchantLane;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 目的地断路器与泳道路由（追问 2）。
 */
class CircuitBreakerLaneTest {

	@Test
	void 健康目的地走主泳道() {
		WebhookProperties properties = new WebhookProperties();
		DestinationCircuitBreakerRegistry registry = new DestinationCircuitBreakerRegistry(properties);
		MerchantLaneRouter router = new MerchantLaneRouter(registry, properties);

		assertSame(MerchantLane.MAIN, router.route("ep_healthy"));
	}

	@Test
	void 持续失败后跳闸并转入慢泳道() {
		WebhookProperties properties = new WebhookProperties();
		properties.getCircuit().setSlidingWindowSize(20);
		properties.getCircuit().setMinimumNumberOfCalls(5);
		properties.getCircuit().setFailureRateThreshold(50f);
		properties.getCircuit().setMaxOfflineMinutes(30);

		DestinationCircuitBreakerRegistry registry = new DestinationCircuitBreakerRegistry(properties);
		MerchantLaneRouter router = new MerchantLaneRouter(registry, properties);
		CircuitBreaker breaker = registry.forDestination("ep_broken");

		assertSame(MerchantLane.MAIN, router.route("ep_broken"));

		for (int i = 0; i < 20 && breaker.tryAcquirePermission(); i++) {
			breaker.onError(1000, TimeUnit.NANOSECONDS, new RuntimeException("HTTP 500"));
		}
		assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
		assertSame(MerchantLane.SLOW, router.route("ep_broken"));
	}

	@Test
	void 离线超过阈值后直接取消不再消耗资源() {
		WebhookProperties properties = new WebhookProperties();
		properties.getCircuit().setSlidingWindowSize(5);
		properties.getCircuit().setMinimumNumberOfCalls(2);
		properties.getCircuit().setFailureRateThreshold(50f);
		properties.getCircuit().setMaxOfflineMinutes(0);

		DestinationCircuitBreakerRegistry registry = new DestinationCircuitBreakerRegistry(properties);
		MerchantLaneRouter router = new MerchantLaneRouter(registry, properties);
		CircuitBreaker breaker = registry.forDestination("ep_offline");

		for (int i = 0; i < 5 && breaker.tryAcquirePermission(); i++) {
			breaker.onError(1000, TimeUnit.NANOSECONDS, new RuntimeException("timeout"));
		}
		assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
		assertSame(MerchantLane.QUARANTINE, router.route("ep_offline"));
	}

	@Test
	void 断路器按目的地隔离不会误伤健康商户() {
		WebhookProperties properties = new WebhookProperties();
		properties.getCircuit().setSlidingWindowSize(5);
		properties.getCircuit().setMinimumNumberOfCalls(2);
		properties.getCircuit().setFailureRateThreshold(50f);

		DestinationCircuitBreakerRegistry registry = new DestinationCircuitBreakerRegistry(properties);
		MerchantLaneRouter router = new MerchantLaneRouter(registry, properties);
		CircuitBreaker broken = registry.forDestination("ep_broken");
		for (int i = 0; i < 5 && broken.tryAcquirePermission(); i++) {
			broken.onError(1000, TimeUnit.NANOSECONDS, new RuntimeException("HTTP 500"));
		}

		assertSame(MerchantLane.SLOW, router.route("ep_broken"));
		assertSame(MerchantLane.MAIN, router.route("ep_healthy"), "健康商户不能被别人拖进慢泳道");
	}
}
