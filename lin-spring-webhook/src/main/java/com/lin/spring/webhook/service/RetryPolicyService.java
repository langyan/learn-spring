package com.lin.spring.webhook.service;

import com.lin.spring.webhook.config.WebhookProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 指数退避策略（对齐面试追问 1）。
 *
 * <p>序列默认 1 分钟 → 5 分钟 → 1 小时 → 3 小时 → 6 小时 → 12 小时 → 24 小时，
 * 之后维持 24 小时，总窗口上限 3 天；每次再叠加 ±20% 抖动，避免同一商户的重试同时到期造成惊群。</p>
 */
@Slf4j
@Service
public class RetryPolicyService {

	private final WebhookProperties properties;

	public RetryPolicyService(WebhookProperties properties) {
		this.properties = properties;
	}

	/**
	 * 计算第 attempted 次尝试后应等待的秒数。
	 *
	 * @param attempted 已尝试次数（从 1 开始）
	 * @return 退避秒数
	 */
	public long nextDelaySeconds(int attempted) {
		List<Long> backoff = properties.getRetry().getBackoffSeconds();
		int index = Math.min(Math.max(attempted - 1, 0), backoff.size() - 1);
		return withJitter(backoff.get(index));
	}

	/**
	 * 是否还应继续重试。
	 *
	 * @param attempted      已尝试次数
	 * @param eventCreatedAt 事件创建时间
	 * @return true 表示继续重试
	 */
	public boolean shouldRetry(int attempted, Instant eventCreatedAt) {
		if (attempted >= properties.getRetry().getMaxAttempts()) {
			log.debug("达到最大尝试次数 {}，停止重试", properties.getRetry().getMaxAttempts());
			return false;
		}
		Instant createdAt = eventCreatedAt == null ? Instant.now() : eventCreatedAt;
		long elapsedDays = Duration.between(createdAt, Instant.now()).toDays();
		if (elapsedDays >= properties.getRetry().getMaxDurationDays()) {
			log.debug("超过重试窗口 {} 天，停止重试", properties.getRetry().getMaxDurationDays());
			return false;
		}
		return true;
	}

	private long withJitter(long base) {
		double ratio = properties.getRetry().getJitterRatio();
		if (ratio <= 0) {
			return base;
		}
		double factor = 1 + ThreadLocalRandom.current().nextDouble(-ratio, ratio);
		return Math.max(1L, Math.round(base * factor));
	}
}
