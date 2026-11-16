package com.lin.spring.events.observability;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 调度器生命周期状态机：STOPPED → STARTING → RUNNING →（失败）FAILED。
 * <p>
 * 核心不变量：没有已调度任务且当前无在执行的工作时，调度器<b>不得</b>报告 RUNNING。
 * {@code schedule(nextRun)} 被拒绝（如线程池已关闭）时立即转 FAILED，让故障可见，
 * 而不是静默地保持一个「看起来健康」的 RUNNING 状态。
 * </p>
 */
public class SchedulerLifecycle {

	/** 生命周期状态。 */
	public enum State {
		/** 尚未启动。 */
		STOPPED,
		/** 正在启动（首轮任务已提交但未运行）。 */
		STARTING,
		/** 有已调度的任务或正在执行的 tick。 */
		RUNNING,
		/** 调度失败：不再有未来执行，需要人工介入或自动恢复。 */
		FAILED
	}

	private final String name;

	private final AtomicReference<State> state = new AtomicReference<>(State.STOPPED);

	/** 是否已成功提交下一次执行（与 state 共同构成不变量判定）。 */
	private volatile boolean scheduledNext = false;

	/** 是否正在执行 tick。 */
	private volatile boolean ticking = false;

	/** 最近一次失败原因。 */
	private volatile String lastFailureReason;

	public SchedulerLifecycle(String name) {
		this.name = name;
	}

	/**
	 * 启动：进入 STARTING。
	 */
	public void starting() {
		state.set(State.STARTING);
	}

	/**
	 * tick 开始执行。
	 */
	public void tickStarted() {
		ticking = true;
		if (state.get() != State.FAILED) {
			state.set(State.RUNNING);
		}
	}

	/**
	 * 成功提交下一次执行：保证未来仍有任务运行，状态可安全置为 RUNNING。
	 */
	public void nextScheduled() {
		scheduledNext = true;
		State current = state.get();
		if (current == State.STARTING || (current == State.FAILED)) {
			state.set(State.RUNNING);
		}
	}

	/**
	 * tick 结束：若无已调度后续任务，则不得报告 RUNNING（不变量）。
	 */
	public void tickFinished() {
		ticking = false;
		if (!scheduledNext && state.get() != State.FAILED) {
			// 理论上不应发生（tick 结尾必先提交下次调度）；防御性降级为 STOPPED 使问题可见
			state.set(State.STOPPED);
		}
	}

	/**
	 * 调度被拒绝等致命失败：进入 FAILED，停止谎报 RUNNING。
	 */
	public void failed(String reason) {
		lastFailureReason = reason;
		state.set(State.FAILED);
	}

	/**
	 * 下一次调度提交被拒绝后的补偿标记。
	 */
	public void nextScheduleRejected() {
		scheduledNext = false;
	}

	public State state() {
		return state.get();
	}

	public boolean isFailed() {
		return state.get() == State.FAILED;
	}

	public String lastFailureReason() {
		return lastFailureReason;
	}

	public String name() {
		return name;
	}

	/**
	 * 是否健康：非 FAILED。
	 */
	public boolean healthy() {
		return !isFailed();
	}
}
