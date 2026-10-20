package com.lin.spring.webhook.isolation;

import com.lin.spring.webhook.config.WebhookProperties;
import com.lin.spring.webhook.domain.MerchantLane;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 泳道路由器：决定一条任务走主队列、延迟队列还是直接取消。
 *
 * <p>这一步就是面试追问 2 的答案：系统<b>自动</b>（无需人工干预）把出问题的目的地
 * 挪出主投递池，健康商户完全不受影响。</p>
 */
@Component
public class MerchantLaneRouter {

	private final DestinationCircuitBreakerRegistry breakerRegistry;
	private final WebhookProperties properties;

	public MerchantLaneRouter(DestinationCircuitBreakerRegistry breakerRegistry, WebhookProperties properties) {
		this.breakerRegistry = breakerRegistry;
		this.properties = properties;
	}

	/**
	 * 判定某个端点当前应处在哪条泳道。
	 *
	 * @param endpointId 端点 ID
	 * @return 泳道
	 */
	public MerchantLane route(String endpointId) {
		CircuitBreaker breaker = breakerRegistry.forDestination(endpointId);
		CircuitBreaker.State state = breaker.getState();
		if (state != CircuitBreaker.State.OPEN && state != CircuitBreaker.State.FORCED_OPEN) {
			return MerchantLane.MAIN;
		}
		Instant since = breakerRegistry.openSince(endpointId).orElse(Instant.now());
		long openMinutes = Duration.between(since, Instant.now()).toMinutes();
		if (openMinutes >= properties.getCircuit().getMaxOfflineMinutes()) {
			return MerchantLane.QUARANTINE;
		}
		return MerchantLane.SLOW;
	}
}
