package com.lin.spring.events.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 调度器配置：提供单线程 {@link TaskScheduler} Bean。
 * <p>
 * 派发器/重试器/回收器不使用 {@code @Scheduled} 注解，而是显式持有该调度器并在每次 tick
 * 结尾自调度下一次运行，从而能捕获调度失败并驱动生命周期状态机（见 SchedulerLifecycle）。
 * </p>
 */
@Configuration
public class SchedulingConfig implements SchedulingConfigurer {

	/** 调度线程名，便于日志与线程 dump 排查。 */
	public static final String SCHEDULER_BEAN_NAME = "eventsTaskScheduler";

	@Bean(name = SCHEDULER_BEAN_NAME, destroyMethod = "shutdown")
	public ThreadPoolTaskScheduler eventsTaskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(1);
		scheduler.setThreadNamePrefix("events-sched-");
		// 应用关闭时等待在跑的 tick 结束，避免中途丢状态
		scheduler.setWaitForTasksToCompleteOnShutdown(true);
		scheduler.setAwaitTerminationSeconds(10);
		return scheduler;
	}

	@Override
	public void configureTasks(org.springframework.scheduling.config.ScheduledTaskRegistrar registrar) {
		// 无 @Scheduled 任务；保留接口实现以绑定自定义调度器
		registrar.setTaskScheduler(eventsTaskScheduler());
	}
}
