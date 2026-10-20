package com.lin.spring.webhook.controller;

import com.lin.spring.webhook.entity.WebhookEventEntity;
import com.lin.spring.webhook.service.DeliveryLogService;
import com.lin.spring.webhook.service.EventIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 死信队列查询与人工重放。
 */
@RestController
@RequestMapping("/api/dlq")
@RequiredArgsConstructor
public class DeliveryController {

	private final DeliveryLogService deliveryLogService;
	private final EventIngestionService ingestionService;

	@GetMapping
	public List<WebhookEventEntity> deadLetters() {
		return deliveryLogService.deadLetters();
	}

	/**
	 * 重放一条死信事件。
	 */
	@PostMapping("/{eventId}/replay")
	public Map<String, Object> replay(@PathVariable String eventId) {
		WebhookEventEntity entity = ingestionService.replay(eventId);
		return Map.of("eventId", entity.getEventId(), "status", entity.getStatus().name(),
			"message", "已重新入队");
	}
}
