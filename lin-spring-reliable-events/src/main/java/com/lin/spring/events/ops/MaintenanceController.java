package com.lin.spring.events.ops;

import com.lin.spring.events.config.EventsProperties;
import com.lin.spring.events.inbox.entity.InboxStatus;
import com.lin.spring.events.inbox.repository.InboxEventRepository;
import com.lin.spring.events.outbox.OutboxDispatcherScheduler;
import com.lin.spring.events.outbox.entity.OutboxStatus;
import com.lin.spring.events.outbox.repository.OutboxEventRepository;
import com.lin.spring.events.retry.InboxRetryScheduler;
import com.lin.spring.events.retry.RetentionService;
import com.lin.spring.events.retry.StaleLockRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 维护接口：基础设施状态总览、陈旧锁回收与 TTL 清理。
 */
@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

	private final OutboxDispatcherScheduler dispatcherScheduler;

	private final InboxRetryScheduler retryScheduler;

	private final StaleLockRecoveryService recoveryService;

	private final RetentionService retentionService;

	private final OutboxEventRepository outboxEventRepository;

	private final InboxEventRepository inboxEventRepository;

	private final EventsProperties properties;

	private final FailureSimulator simulator;

	/**
	 * 基础设施状态：调度器生命周期、状态计数、锁与故障开关。
	 */
	@GetMapping("/status")
	public ResponseEntity<Map<String, Object>> status() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("instanceId", properties.getInstanceId());

		Map<String, Object> schedulers = new LinkedHashMap<>();
		schedulers.put("outboxDispatcher", schedulerDetail(dispatcherScheduler.lifecycle));
		schedulers.put("inboxRetry", schedulerDetail(retryScheduler.lifecycle));
		body.put("schedulers", schedulers);

		Map<String, Object> outbox = new LinkedHashMap<>();
		for (OutboxStatus s : OutboxStatus.values()) {
			outbox.put(s.name(), outboxEventRepository.countByStatus(s));
		}
		body.put("outbox", outbox);

		Map<String, Object> inbox = new LinkedHashMap<>();
		for (InboxStatus s : InboxStatus.values()) {
			inbox.put(s.name(), inboxEventRepository.countByStatus(s));
		}
		body.put("inbox", inbox);

		body.put("simulator", simulator.status());
		return ResponseEntity.ok(body);
	}

	private Map<String, Object> schedulerDetail(com.lin.spring.events.observability.SchedulerLifecycle lifecycle) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("state", lifecycle.state().name());
		detail.put("healthy", lifecycle.healthy());
		detail.put("lastFailureReason", lifecycle.lastFailureReason());
		return detail;
	}

	/**
	 * 手动回收陈旧锁（超时 PROCESSING）。
	 */
	@PostMapping("/recover-stale")
	public ResponseEntity<String> recoverStale() {
		int recovered = recoveryService.recoverNow();
		return ResponseEntity.ok("回收 " + recovered + " 条陈旧 PROCESSING 记录");
	}

	/**
	 * 手动 TTL 清理。
	 */
	@PostMapping("/cleanup")
	public ResponseEntity<String> cleanup() {
		int deleted = retentionService.cleanupNow();
		return ResponseEntity.ok("清理 " + deleted + " 条已完结记录");
	}
}
