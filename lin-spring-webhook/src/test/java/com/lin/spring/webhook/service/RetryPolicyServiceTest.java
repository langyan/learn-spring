package com.lin.spring.webhook.service;

import com.lin.spring.webhook.config.WebhookProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 指数退避策略（追问 1）。
 */
class RetryPolicyServiceTest {

	private final WebhookProperties properties = new WebhookProperties();
	private final RetryPolicyService policy = new RetryPolicyService(properties);

	@Test
	void 退避序列依次为1分钟5分钟1小时() {
		properties.getRetry().setBackoffSeconds(List.of(60L, 300L, 3600L));
		properties.getRetry().setJitterRatio(0);

		assertEqualsApprox(60, policy.nextDelaySeconds(1));
		assertEqualsApprox(300, policy.nextDelaySeconds(2));
		assertEqualsApprox(3600, policy.nextDelaySeconds(3));
	}

	@Test
	void 超出序列长度后维持最后一次退避() {
		properties.getRetry().setBackoffSeconds(List.of(60L, 300L, 3600L));
		properties.getRetry().setJitterRatio(0);

		assertEqualsApprox(3600, policy.nextDelaySeconds(10));
	}

	@Test
	void 抖动落在正负二十百分比区间内() {
		properties.getRetry().setBackoffSeconds(List.of(60L));
		properties.getRetry().setJitterRatio(0.2);

		for (int i = 0; i < 200; i++) {
			long delay = policy.nextDelaySeconds(1);
			assertTrue(delay >= 48 && delay <= 72, "退避值应落在 ±20% 内，实际 " + delay);
		}
	}

	@Test
	void 超过最大尝试次数后停止重试() {
		properties.getRetry().setMaxAttempts(8);

		assertTrue(policy.shouldRetry(7, Instant.now()));
		assertFalse(policy.shouldRetry(8, Instant.now()));
	}

	@Test
	void 超过三天窗口后停止重试() {
		properties.getRetry().setMaxAttempts(100);

		assertTrue(policy.shouldRetry(1, Instant.now().minus(Duration.ofDays(2))));
		assertFalse(policy.shouldRetry(1, Instant.now().minus(Duration.ofDays(4))));
	}

	private void assertEqualsApprox(long expected, long actual) {
		assertTrue(Math.abs(expected - actual) <= Math.max(1, expected / 5),
			"期望约 " + expected + " 秒，实际 " + actual + " 秒");
	}
}
