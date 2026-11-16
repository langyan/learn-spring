package com.lin.spring.events.ops;

import com.lin.spring.events.inbox.InboxService;
import com.lin.spring.events.inbox.entity.InboxStatus;
import com.lin.spring.events.inbox.repository.InboxEventRepository;
import com.lin.spring.events.ops.dto.EventViews;
import com.lin.spring.events.retry.InboxRetryScheduler;
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
 * Inbox 运维接口：状态查询、手动重放、重复投递演示与手动重试 tick。
 */
@RestController
@RequestMapping("/api/inbox")
@RequiredArgsConstructor
public class InboxOpsController {

	private final InboxEventRepository inboxEventRepository;

	private final InboxService inboxService;

	private final InboxRetryScheduler retryScheduler;

	/**
	 * 按状态分页查询。
	 */
	@GetMapping
	public ResponseEntity<List<EventViews.InboxView>> list(
		@RequestParam(required = false) InboxStatus status,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "50") int size) {
		List<EventViews.InboxView> views = (status == null
			? inboxEventRepository.findAll(PageRequest.of(page, Math.min(size, 200))).getContent()
			: inboxEventRepository.findByStatusOrderByReceivedAtDesc(status, PageRequest.of(page, Math.min(size, 200))))
			.stream().map(EventViews.InboxView::from).toList();
		return ResponseEntity.ok(views);
	}

	/**
	 * 按 eventId 查询单条（处理状态排查）。
	 */
	@GetMapping("/{eventId}")
	public ResponseEntity<EventViews.InboxView> get(@PathVariable String eventId) {
		return ResponseEntity.of(inboxEventRepository.findByEventId(eventId)
			.map(EventViews.InboxView::from));
	}

	/**
	 * 手动重放 FAILED 事件：重置为 RECEIVED 立即重试。
	 */
	@PostMapping("/{eventId}/retry")
	public ResponseEntity<String> retry(@PathVariable String eventId) {
		boolean replayed = inboxService.replay(eventId);
		if (!replayed) {
			throw new ResponseStatusException(NOT_FOUND, "记录不存在或状态不是 FAILED: " + eventId);
		}
		retryScheduler.retryOnce();
		return ResponseEntity.ok("已重置为 RECEIVED 并触发重试");
	}

	/**
	 * 幂等演示：把已存在的事件按原始载荷「再投递一次」，应返回 DUPLICATE。
	 */
	@PostMapping("/{eventId}/duplicate")
	public ResponseEntity<String> duplicate(@PathVariable String eventId) {
		var event = inboxEventRepository.findByEventId(eventId)
			.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "事件不存在: " + eventId));
		InboxService.ReceiveResult result = inboxService.receive(
			event.getEventId(), event.getEventType(), event.getSource(),
			event.getCorrelationId(), event.getPayload());
		return ResponseEntity.ok("重投结果: " + result);
	}

	/**
	 * 手动触发一次重试 tick。
	 */
	@PostMapping("/retry-once")
	public ResponseEntity<String> retryOnce() {
		return ResponseEntity.ok("本批处理 " + retryScheduler.retryOnce() + " 条");
	}
}
