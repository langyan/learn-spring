package com.lin.spring.webhook.isolation;

import com.lin.spring.webhook.config.WebhookProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按「目的地（端点）」维度动态创建断路器。
 *
 * <p>隔离粒度必须是目的地而不是全局：全局熔断会让健康商户跟着一起被误伤。</p>
 */
@Component
public class DestinationCircuitBreakerRegistry {

	private final CircuitBreakerRegistry registry;
	private final ConcurrentHashMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Instant> openSince = new ConcurrentHashMap<>();

	public DestinationCircuitBreakerRegistry(WebhookProperties properties) {
		WebhookProperties.Circuit circuit = properties.getCircuit();
		CircuitBreakerConfig config = CircuitBreakerConfig.custom()
			.failureRateThreshold(circuit.getFailureRateThreshold())
			.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
			.slidingWindowSize(circuit.getSlidingWindowSize())
			.minimumNumberOfCalls(circuit.getMinimumNumberOfCalls())
			.waitDurationInOpenState(Duration.ofSeconds(circuit.getWaitDurationInOpenStateSeconds()))
			.permittedNumberOfCallsInHalfOpenState(circuit.getPermittedNumberOfCallsInHalfOpenState())
			.build();
		this.registry = CircuitBreakerRegistry.of(config);
	}

	/**
	 * 取（或创建）某个端点的断路器。
	 *
	 * @param endpointId 端点 ID
	 * @return 该目的地专属的断路器
	 */
	public CircuitBreaker forDestination(String endpointId) {
		return breakers.computeIfAbsent(endpointId, id -> {
			CircuitBreaker breaker = registry.circuitBreaker(id);
			breaker.getEventPublisher().onStateTransition(event -> {
				CircuitBreaker.State to = event.getStateTransition().getToState();
				if (to == CircuitBreaker.State.OPEN || to == CircuitBreaker.State.FORCED_OPEN) {
					openSince.putIfAbsent(id, Instant.now());
				} else {
					openSince.remove(id);
				}
			});
			return breaker;
		});
	}

	/**
	 * 目的地处于熔断状态的起始时间，用于判定商户是否已彻底离线。
	 *
	 * @param endpointId 端点 ID
	 * @return 熔断开始时间
	 */
	public Optional<Instant> openSince(String endpointId) {
		return Optional.ofNullable(openSince.get(endpointId));
	}

	public Map<String, String> snapshot() {
		Map<String, String> view = new java.util.LinkedHashMap<>();
		breakers.forEach((id, breaker) -> view.put(id, breaker.getState().name()));
		return view;
	}
}
