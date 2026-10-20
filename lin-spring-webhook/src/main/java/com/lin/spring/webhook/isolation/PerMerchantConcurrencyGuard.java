package com.lin.spring.webhook.isolation;

import com.lin.spring.webhook.config.WebhookProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * 每商户并发配额（Bulkhead 思路）。
 *
 * <p>它和断路器是互补的两层：配额限制「大流量慢商户」瞬时占据多少投递线程，
 * 断路器负责「持续故障的目的地」长期消耗资源。配额拿不到时立即回延迟队列，绝不阻塞。</p>
 */
@Component
public class PerMerchantConcurrencyGuard {

	private final int permits;
	private final ConcurrentHashMap<String, Semaphore> semaphores = new ConcurrentHashMap<>();

	public PerMerchantConcurrencyGuard(WebhookProperties properties) {
		this.permits = Math.max(1, properties.getIsolation().getPerMerchantMaxConcurrency());
	}

	/**
	 * 尝试为某个商户申请一个投递槽位（非阻塞）。
	 *
	 * @param merchantId 商户 ID
	 * @return 是否拿到
	 */
	public boolean tryAcquire(String merchantId) {
		return semaphore(merchantId).tryAcquire();
	}

	public void release(String merchantId) {
		semaphore(merchantId).release();
	}

	/**
	 * 该商户当前在途的投递数。
	 */
	public int inFlight(String merchantId) {
		Semaphore semaphore = semaphores.get(merchantId);
		return semaphore == null ? 0 : (permits - semaphore.availablePermits());
	}

	public int permits() {
		return permits;
	}

	private Semaphore semaphore(String merchantId) {
		return semaphores.computeIfAbsent(merchantId, key -> new Semaphore(permits, true));
	}
}
