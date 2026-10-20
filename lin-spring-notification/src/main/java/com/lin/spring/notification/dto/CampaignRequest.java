package com.lin.spring.notification.dto;

import com.lin.spring.notification.domain.NotificationChannel;
import com.lin.spring.notification.domain.NotificationType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

/**
 * 批量营销活动请求（演示：不写 DB 千万行，直接流式发 Kafka）。
 */
public record CampaignRequest(
	@NotEmpty List<String> userIds,
	@NotNull NotificationType type,
	@NotNull NotificationChannel channel,
	String templateId,
	Map<String, Object> data,
	String campaignId
) {
}
