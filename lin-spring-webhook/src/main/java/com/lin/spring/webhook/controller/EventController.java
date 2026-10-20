package com.lin.spring.webhook.controller;

import com.lin.spring.webhook.dto.DeliveryAttemptView;
import com.lin.spring.webhook.dto.IngestEventRequest;
import com.lin.spring.webhook.entity.WebhookEventEntity;
import com.lin.spring.webhook.service.DeliveryLogService;
import com.lin.spring.webhook.service.EventIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 事件接入与查询接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

	private final EventIngestionService ingestionService;
	private final DeliveryLogService deliveryLogService;

	/**
	 * 内部服务提交事件。立即返回 202，投递在后台异步进行。
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public Map<String, Object> ingest(@Valid @RequestBody IngestEventRequest request) {
		WebhookEventEntity entity = ingestionService.ingest(request);
		return Map.of(
			"eventId", entity.getEventId(),
			"status", entity.getStatus().name(),
			"message", "事件已接受，保证会被尝试投递");
	}

	@GetMapping("/{eventId}")
	public WebhookEventEntity get(@PathVariable String eventId) {
		return deliveryLogService.event(eventId)
			.orElseThrow(() -> new IllegalArgumentException("事件不存在: " + eventId));
	}

	/**
	 * 查询某事件的全部投递尝试流水。
	 */
	@GetMapping("/{eventId}/attempts")
	public List<DeliveryAttemptView> attempts(@PathVariable String eventId) {
		return deliveryLogService.attemptsFor(eventId).stream()
			.map(DeliveryAttemptView::from)
			.toList();
	}
}
