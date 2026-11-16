package com.lin.spring.events.retry;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 指数退避纯函数：第 n 次失败后的下次可执行延迟。
 * <ul>
 *   <li>公式：delay = base * 2^(attempts - 1)，上限 max</li>
 *   <li>抖动：delay * (1 ± jitterRatio)，下限 1ms，避免同一批失败事件同时到期造成惊群</li>
 * </ul>
 * 序列示例（base=1s, jitter=0）：1s → 2s → 4s → 8s → 16s …
 */
public final class ExponentialBackoff {

	private ExponentialBackoff() {
	}

	/**
	 * 计算退避延迟（毫秒）。
	 *
	 * @param failedAttempts 已失败次数（从 1 开始）
	 * @param baseDelayMs   基础延迟
	 * @param maxDelayMs    单次上限
	 * @param jitterRatio   抖动比例（0 ~ 1，0 表示无抖动）
	 */
	public static long computeDelayMs(long failedAttempts, long baseDelayMs, long maxDelayMs, double jitterRatio) {
		if (failedAttempts < 1) {
			failedAttempts = 1;
		}
		// 位移防溢出：指数增长很快触碰上限，超过 62 次幂直接取上限
		int shift = failedAttempts >= 63 ? 62 : (int) (failedAttempts - 1);
		long delay;
		if (baseDelayMs > Long.MAX_VALUE >> shift) {
			delay = maxDelayMs;
		} else {
			delay = Math.min(baseDelayMs << shift, maxDelayMs);
		}
		return withJitter(delay, jitterRatio);
	}

	private static long withJitter(long delayMs, double jitterRatio) {
		if (jitterRatio <= 0) {
			return delayMs;
		}
		double factor = 1 + ThreadLocalRandom.current().nextDouble(-jitterRatio, jitterRatio);
		return Math.max(1, Math.round(delayMs * factor));
	}
}
