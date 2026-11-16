package com.lin.spring.events.ops;

import com.lin.spring.events.inbox.repository.InboxEventRepository;
import com.lin.spring.events.ops.dto.EventViews;
import com.lin.spring.events.outbox.OutboxDispatcherScheduler;
import com.lin.spring.events.outbox.entity.OutboxStatus;
import com.lin.spring.events.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Outbox 运维接口：状态查询、链路追踪、手动重放与手动派发。
 */
@RestController
@RequestMapping("/api/outbox")
@RequiredArgsConstructor
public class OutboxOpsController {

	private final OutboxEventRepository outboxEventRepository;

	private final InboxEventRepository inboxEventRepository;

	private final OutboxDispatcherScheduler dispatcherScheduler;

	private final com.lin.spring.events.outbox.OutboxDispatchService dispatchService;

	/**
	 * 按状态分页查询（不传 status 查全部前 100 条）。
	 */
	@GetMapping
	public ResponseEntity<List<EventViews.OutboxView>> list(
		@RequestParam(required = false) OutboxStatus status,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "50") int size) {
		List<EventViews.OutboxView> views = (status == null
			? outboxEventRepository.findAll(PageRequest.of(page, Math.min(size, 200))).getContent()
			: outboxEventRepository.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(page, Math.min(size, 200))))
			.stream().map(EventViews.OutboxView::from).toList();
		return ResponseEntity.ok(views);
	}

	/**
	 * 端到端链路：同一 correlationId 下的 Outbox 与 Inbox 记录。
	 */
	@GetMapping("/trace/{correlationId}")
	public ResponseEntity<EventViews.TraceView> trace(@PathVariable String correlationId) {
		return ResponseEntity.ok(new EventViews.TraceView(
			correlationId,
			outboxEventRepository.findByCorrelationIdOrderByCreatedAtAsc(correlationId)
				.stream().map(EventViews.OutboxView::from).toList(),
			inboxEventRepository.findByCorrelationIdOrderByReceivedAtAsc(correlationId)
				.stream().map(EventViews.InboxView::from).toList()
		));
	}

	/**
	 * 手动重放 FAILED 事件：重置为 PENDING 立即派发。
	 */
	@PostMapping("/{id}/retry")
	public ResponseEntity<String> retry(@PathVariable Long id) {
		boolean replayed = dispatchService.replayFailed(id);
		if (!replayed) {
			throw new ResponseStatusException(NOT_FOUND, "记录不存在或状态不是 FAILED: " + id);
		}
		return ResponseEntity.ok("已重置为 PENDING，等待派发");
	}

	/**
	 * 手动触发一次派发（不等待调度器 tick，测试/演示用）。
	 */
	@PostMapping("/dispatch-once")
	public ResponseEntity<String> dispatchOnce() {
		int processed = dispatcherScheduler.dispatchOnce();
		return ResponseEntity.ok("本批处理 " + processed + " 条");
	}
}
