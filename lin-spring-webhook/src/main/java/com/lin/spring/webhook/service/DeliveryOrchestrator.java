package com.lin.spring.webhook.service;

import com.lin.spring.webhook.config.WebhookProperties;
import com.lin.spring.webhook.delivery.WebhookDeliveryClient;
import com.lin.spring.webhook.domain.DeliveryStatus;
import com.lin.spring.webhook.domain.EndpointStatus;
import com.lin.spring.webhook.domain.FailureType;
import com.lin.spring.webhook.domain.MerchantLane;
import com.lin.spring.webhook.domain.OutcomeKind;
import com.lin.spring.webhook.dto.DeliveryJob;
import com.lin.spring.webhook.dto.DeliveryOutcome;
import com.lin.spring.webhook.dto.DeliveryResult;
import com.lin.spring.webhook.entity.WebhookEndpointEntity;
import com.lin.spring.webhook.exception.SsrfBlockedException;
import com.lin.spring.webhook.isolation.DestinationCircuitBreakerRegistry;
import com.lin.spring.webhook.isolation.MerchantLaneRouter;
import com.lin.spring.webhook.isolation.PerMerchantConcurrencyGuard;
import com.lin.spring.webhook.queue.DeliveryQueue;
import com.lin.spring.webhook.repository.WebhookEndpointRepository;
import com.lin.spring.webhook.security.PinnedTarget;
import com.lin.spring.webhook.security.SsrfGuard;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 投递编排：幂等 → 泳道路由 → 商户配额 → 目的地熔断 → SSRF 校验 → HTTP POST → 结果分类。
 *
 * <p>四个面试追问在这里汇聚。</p>
 */
@Slf4j
@Service
public class DeliveryOrchestrator {

	private final SsrfGuard ssrfGuard;
	private final WebhookDeliveryClient deliveryClient;
	private final DestinationCircuitBreakerRegistry breakerRegistry;
	private final MerchantLaneRouter laneRouter;
	private final PerMerchantConcurrencyGuard concurrencyGuard;
	private final RetryPolicyService retryPolicy;
	private final DeliveryLogService deliveryLogService;
	private final DeliveryQueue queue;
	private final IdempotencyService idempotency;
	private final WebhookEndpointRepository endpointRepository;
	private final WebhookProperties properties;
	private final Counter deliveredCounter;
	private final Counter failedCounter;
	private final Counter retriedCounter;
	private final Counter cancelledCounter;
	private final Counter deferredCounter;
	private final Counter blockedCounter;
	private final Timer deliveryTimer;

	public DeliveryOrchestrator(SsrfGuard ssrfGuard,
								WebhookDeliveryClient deliveryClient,
								DestinationCircuitBreakerRegistry breakerRegistry,
								MerchantLaneRouter laneRouter,
								PerMerchantConcurrencyGuard concurrencyGuard,
								RetryPolicyService retryPolicy,
								DeliveryLogService deliveryLogService,
								DeliveryQueue queue,
								IdempotencyService idempotency,
								WebhookEndpointRepository endpointRepository,
								WebhookProperties properties,
								MeterRegistry meterRegistry) {
		this.ssrfGuard = ssrfGuard;
		this.deliveryClient = deliveryClient;
		this.breakerRegistry = breakerRegistry;
		this.laneRouter = laneRouter;
		this.concurrencyGuard = concurrencyGuard;
		this.retryPolicy = retryPolicy;
		this.deliveryLogService = deliveryLogService;
		this.queue = queue;
		this.idempotency = idempotency;
		this.endpointRepository = endpointRepository;
		this.properties = properties;
		this.deliveredCounter = meterRegistry.counter("webhook.delivered");
		this.failedCounter = meterRegistry.counter("webhook.failed");
		this.retriedCounter = meterRegistry.counter("webhook.retried");
		this.cancelledCounter = meterRegistry.counter("webhook.cancelled");
		this.deferredCounter = meterRegistry.counter("webhook.deferred");
		this.blockedCounter = meterRegistry.counter("webhook.ssrf_blocked");
		this.deliveryTimer = meterRegistry.timer("webhook.delivery.latency");
	}

	/**
	 * 执行一条投递任务。
	 *
	 * @param job 投递任务
	 * @return 处置结论
	 */
	public DeliveryOutcome deliver(DeliveryJob job) {
		String key = job.idempotencyKey();
		if (!idempotency.tryClaim(key)) {
			log.debug("幂等去重命中，跳过 eventId={} endpointId={}", job.eventId(), job.endpointId());
			return DeliveryOutcome.of(OutcomeKind.SKIPPED_DUPLICATE, "同一事件已有投递在处理或已完成");
		}
		try {
			DeliveryOutcome outcome = attemptDelivery(job);
			if (isTerminal(outcome.kind())) {
				idempotency.markDone(key);
			} else {
				idempotency.release(key);
			}
			return outcome;
		} catch (RuntimeException ex) {
			idempotency.release(key);
			throw ex;
		}
	}

	private DeliveryOutcome attemptDelivery(DeliveryJob job) {
		// 追问 2：先按目的地判定泳道，出问题的商户根本进不了主投递池
		MerchantLane lane = laneRouter.route(job.endpointId());
		if (lane == MerchantLane.QUARANTINE) {
			deliveryLogService.markStatus(job.eventId(), DeliveryStatus.CANCELLED,
				"目的地离线超时，投递已取消", null);
			deliveryLogService.recordAttempt(job.eventId(), job.endpointId(), job.attempt(), null,
				OutcomeKind.CANCELLED);
			cancelledCounter.increment();
			return DeliveryOutcome.of(OutcomeKind.CANCELLED, "目的地长期离线，已取消以释放投递资源");
		}

		// 租户并发配额：拿不到就立刻回延迟队列，绝不占用投递线程等待
		if (!concurrencyGuard.tryAcquire(job.merchantId())) {
			queue.pushDelayed(job, properties.getIsolation().getDeferSeconds());
			deferredCounter.increment();
			return DeliveryOutcome.of(OutcomeKind.DEFERRED, "商户并发配额已满，稍后重试");
		}
		try {
			CircuitBreaker breaker = breakerRegistry.forDestination(job.endpointId());
			if (!breaker.tryAcquirePermission()) {
				queue.pushDelayed(job, properties.getCircuit().getWaitDurationInOpenStateSeconds());
				deferredCounter.increment();
				return DeliveryOutcome.of(OutcomeKind.DEFERRED, "目的地断路器已打开，转入延迟队列");
			}
			return performHttpDelivery(job, breaker);
		} finally {
			concurrencyGuard.release(job.merchantId());
		}
	}

	private DeliveryOutcome performHttpDelivery(DeliveryJob job, CircuitBreaker breaker) {
		long start = System.nanoTime();
		PinnedTarget target;
		try {
			// 追问 3：解析一次、校验公网、固定 IP
			target = ssrfGuard.validateAndPin(job.url());
		} catch (SsrfBlockedException ex) {
			breaker.releasePermission();
			blockedCounter.increment();
			endpointRepository.findByEndpointId(job.endpointId()).ifPresent(endpoint -> {
				endpoint.setStatus(EndpointStatus.BLOCKED);
				endpointRepository.save(endpoint);
			});
			deliveryLogService.markStatus(job.eventId(), DeliveryStatus.FAILED, ex.getMessage(), null);
			deliveryLogService.recordAttempt(job.eventId(), job.endpointId(), job.attempt(), null,
				OutcomeKind.DEAD_LETTERED);
			failedCounter.increment();
			log.warn("端点 {} 因 SSRF 被停用: {}", job.endpointId(), ex.getMessage());
			return DeliveryOutcome.of(OutcomeKind.DEAD_LETTERED, ex.getMessage());
		}

		DeliveryResult result = deliveryClient.deliver(target, job.payload(), job.eventId(), job.eventType(),
			job.secret());
		long duration = System.nanoTime() - start;
		deliveryTimer.record(duration, TimeUnit.NANOSECONDS);

		if (result.success()) {
			breaker.onSuccess(duration, TimeUnit.NANOSECONDS);
			deliveryLogService.markAttempt(job.eventId(), job.attempt(), DeliveryStatus.DELIVERED, null, null);
			deliveryLogService.recordAttempt(job.eventId(), job.endpointId(), job.attempt(), result,
				OutcomeKind.DELIVERED);
			deliveredCounter.increment();
			return DeliveryOutcome.of(OutcomeKind.DELIVERED, "HTTP " + result.statusCode());
		}

		// 只有「值得重试」的失败才计入熔断；4xx 说明端点可达，不该拖垮断路器
		boolean countsAsFailure = result.failureType() == FailureType.RETRYABLE;
		if (countsAsFailure) {
			breaker.onError(duration, TimeUnit.NANOSECONDS, new DeliveryFailedException(result.error()));
		} else {
			breaker.onSuccess(duration, TimeUnit.NANOSECONDS);
		}
		deliveryLogService.recordAttempt(job.eventId(), job.endpointId(), job.attempt(), result,
			countsAsFailure ? OutcomeKind.RETRY_SCHEDULED : OutcomeKind.DEAD_LETTERED);

		if (result.statusCode() == 410) {
			endpointRepository.findByEndpointId(job.endpointId()).ifPresent(endpoint -> {
				endpoint.setStatus(EndpointStatus.DISABLED);
				endpointRepository.save(endpoint);
			});
		}

		if (!countsAsFailure || !retryPolicy.shouldRetry(job.attempt(), job.eventCreatedAt())) {
			deliveryLogService.markAttempt(job.eventId(), job.attempt(), DeliveryStatus.FAILED,
				result.error(), null);
			failedCounter.increment();
			return DeliveryOutcome.of(OutcomeKind.DEAD_LETTERED, "不可重试或重试超限: " + result.error());
		}

		// 追问 1：指数退避后回到队尾，等待期间不占用任何投递线程
		long delaySeconds = retryPolicy.nextDelaySeconds(job.attempt());
		int nextAttempt = job.attempt() + 1;
		queue.pushDelayed(job.nextAttempt(nextAttempt, laneRouter.route(job.endpointId())), delaySeconds);
		deliveryLogService.markAttempt(job.eventId(), nextAttempt, DeliveryStatus.RETRYING, result.error(),
			Instant.now().plusSeconds(delaySeconds));
		retriedCounter.increment();
		log.info("事件 {} 第 {} 次投递失败，{} 秒后重试: {}", job.eventId(), job.attempt(), delaySeconds,
			result.error());
		return DeliveryOutcome.of(OutcomeKind.RETRY_SCHEDULED, delaySeconds + " 秒后重试");
	}

	private boolean isTerminal(OutcomeKind kind) {
		return kind == OutcomeKind.DELIVERED
			|| kind == OutcomeKind.DEAD_LETTERED
			|| kind == OutcomeKind.CANCELLED;
	}

	/** 投递失败时交给断路器统计的异常载体。 */
	private static class DeliveryFailedException extends RuntimeException {
		DeliveryFailedException(String message) {
			super(message);
		}
	}
}
