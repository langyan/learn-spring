package com.lin.spring.notification.controller;

import com.lin.spring.notification.dto.CampaignRequest;
import com.lin.spring.notification.dto.NotificationRequest;
import com.lin.spring.notification.dto.NotificationResponse;
import com.lin.spring.notification.entity.NotificationEntity;
import com.lin.spring.notification.service.CampaignService;
import com.lin.spring.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 通知 REST API。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;
	private final CampaignService campaignService;

	/**
	 * 发送单条通知（持久化 + 异步投递）。
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public NotificationResponse send(@Valid @RequestBody NotificationRequest request) {
		return notificationService.send(request);
	}

	/**
	 * 查询通知状态。
	 */
	@GetMapping("/{id}")
	public NotificationEntity getById(@PathVariable String id) {
		return notificationService.getById(id);
	}

	/**
	 * 批量营销活动（流式写 Kafka，不写千万行 DB）。
	 */
	@PostMapping("/campaigns")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public Map<String, Object> sendCampaign(@Valid @RequestBody CampaignRequest request) {
		int count = campaignService.sendCampaign(request);
		return Map.of("accepted", count, "message", "营销活动事件已发布到消息队列");
	}
}
