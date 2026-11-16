package com.lin.spring.events.ops;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 故障演练模拟器：内存态开关，供 SimulatorController 注入故障。
 * <ul>
 *   <li>failPublishCount：接下来 N 次投递抛异常（模拟 broker 不可用）</li>
 *   <li>failHandlerCount：接下来 N 次消费处理抛异常（模拟下游业务故障）</li>
 *   <li>crashAfterClaim：投递前直接「假装崩溃」——记录停留在 PROCESSING 不回写，
 *       用于演示陈旧锁回收（另一个实例超时后接手）</li>
 *   <li>duplicateNext：下一次投递后向 Inbox 重放同一事件两次（模拟 at-least-once 重复投递）</li>
 * </ul>
 * <p>仅用于教学演示，生产环境请勿使用。</p>
 */
@Component
public class FailureSimulator {

	private final AtomicInteger failPublishCount = new AtomicInteger();

	private final AtomicInteger failHandlerCount = new AtomicInteger();

	private final AtomicReference<Boolean> crashAfterClaim = new AtomicReference<>(false);

	private final AtomicReference<Boolean> duplicateNext = new AtomicReference<>(false);

	@Data
	public static class Status {
		private int failPublishRemaining;
		private int failHandlerRemaining;
		private boolean crashAfterClaim;
		private boolean duplicateNext;
	}

	/**
	 * 注入接下来的投递失败次数。
	 */
	public void failNextPublishes(int n) {
		failPublishCount.set(n);
	}

	/**
	 * 注入接下来的消费处理失败次数。
	 */
	public void failNextHandlers(int n) {
		failHandlerCount.set(n);
	}

	/**
	 * 开关「申领后崩溃」。
	 */
	public void setCrashAfterClaim(boolean enabled) {
		crashAfterClaim.set(enabled);
	}

	/**
	 * 开关「下一次投递重复两份」。
	 */
	public void setDuplicateNext(boolean enabled) {
		duplicateNext.set(enabled);
	}

	/**
	 * 派发器调用：若命中失败注入则抛异常。
	 */
	public void maybeFailPublish(String eventId) {
		int remaining = failPublishCount.get();
		if (remaining > 0 && failPublishCount.compareAndSet(remaining, remaining - 1)) {
			throw new IllegalStateException("simulated publish failure for event " + eventId);
		}
	}

	/**
	 * 派发器调用：是否在投递前模拟持锁崩溃（不回写状态）。
	 */
	public boolean shouldCrashAfterClaim() {
		return crashAfterClaim.getAndSet(false);
	}

	/**
	 * 消费侧调用：若命中失败注入则抛异常。
	 */
	public void maybeFailHandler(String eventId) {
		int remaining = failHandlerCount.get();
		if (remaining > 0 && failHandlerCount.compareAndSet(remaining, remaining - 1)) {
			throw new IllegalStateException("simulated handler failure for event " + eventId);
		}
	}

	/**
	 * 消费侧调用：本次投递是否需要重复两份。
	 */
	public boolean shouldDuplicateNext() {
		return duplicateNext.getAndSet(false);
	}

	/**
	 * 当前开关状态（运维接口展示）。
	 */
	public Status status() {
		Status s = new Status();
		s.setFailPublishRemaining(failPublishCount.get());
		s.setFailHandlerRemaining(failHandlerCount.get());
		s.setCrashAfterClaim(crashAfterClaim.get());
		s.setDuplicateNext(duplicateNext.get());
		return s;
	}

	/**
	 * 全部复位。
	 */
	public void reset() {
		failPublishCount.set(0);
		failHandlerCount.set(0);
		crashAfterClaim.set(false);
		duplicateNext.set(false);
	}
}
