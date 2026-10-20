package com.lin.spring.notification.provider;

/**
 * 简易令牌桶限流器，用于演示 Provider 背压。
 */
public class SimpleRateLimiter {

	private final double permitsPerSecond;
	private double storedPermits;
	private long lastRefillNanos;

	/**
	 * 创建限流器。
	 *
	 * @param permitsPerSecond 每秒许可数（TPS）
	 */
	public SimpleRateLimiter(double permitsPerSecond) {
		this.permitsPerSecond = permitsPerSecond;
		this.storedPermits = permitsPerSecond;
		this.lastRefillNanos = System.nanoTime();
	}

	/**
	 * 获取一个许可，必要时阻塞等待。
	 */
	public synchronized void acquire() {
		refill();
		while (storedPermits < 1.0) {
			long waitNanos = (long) ((1.0 - storedPermits) / permitsPerSecond * 1_000_000_000L);
			try {
				Thread.sleep(Math.max(1, waitNanos / 1_000_000L));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
			refill();
		}
		storedPermits -= 1.0;
	}

	private void refill() {
		long now = System.nanoTime();
		double elapsed = (now - lastRefillNanos) / 1_000_000_000.0;
		if (elapsed > 0) {
			storedPermits = Math.min(permitsPerSecond, storedPermits + elapsed * permitsPerSecond);
			lastRefillNanos = now;
		}
	}
}
