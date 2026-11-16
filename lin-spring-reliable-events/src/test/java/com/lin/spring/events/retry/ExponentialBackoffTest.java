package com.lin.spring.events.retry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 指数退避纯函数测试：序列、上限截断与抖动范围。
 */
class ExponentialBackoffTest {

	@Test
	@DisplayName("退避序列应为 1s/2s/4s/8s/16s（无抖动）")
	void sequenceWithoutJitter() {
		long base = 1000, max = 600_000;
		assertThat(ExponentialBackoff.computeDelayMs(1, base, max, 0)).isEqualTo(1000);
		assertThat(ExponentialBackoff.computeDelayMs(2, base, max, 0)).isEqualTo(2000);
		assertThat(ExponentialBackoff.computeDelayMs(3, base, max, 0)).isEqualTo(4000);
		assertThat(ExponentialBackoff.computeDelayMs(4, base, max, 0)).isEqualTo(8000);
		assertThat(ExponentialBackoff.computeDelayMs(5, base, max, 0)).isEqualTo(16000);
	}

	@Test
	@DisplayName("退避延迟不得超过上限")
	void cappedByMax() {
		assertThat(ExponentialBackoff.computeDelayMs(20, 1000, 5000, 0)).isEqualTo(5000);
		assertThat(ExponentialBackoff.computeDelayMs(100, 1000, 5000, 0)).isEqualTo(5000);
	}

	@Test
	@DisplayName("抖动应在 ±ratio 范围内且下限为 1ms")
	void jitterBounds() {
		long base = 1000, max = 60_000;
		for (int i = 0; i < 500; i++) {
			// attempts=3 → 基准 4000ms，±50% → [2000, 6000]
			long delay = ExponentialBackoff.computeDelayMs(3, base, max, 0.5);
			assertThat(delay).isBetween(2000L, 6000L);
		}
	}

	@Test
	@DisplayName("非法 attempts 视为 1")
	void invalidAttempts() {
		assertThat(ExponentialBackoff.computeDelayMs(0, 1000, 60_000, 0)).isEqualTo(1000);
		assertThat(ExponentialBackoff.computeDelayMs(-5, 1000, 60_000, 0)).isEqualTo(1000);
	}
}
