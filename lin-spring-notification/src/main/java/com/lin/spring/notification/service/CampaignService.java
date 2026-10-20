package com.lin.spring.notification.service;

import com.lin.spring.notification.dto.CampaignRequest;
import com.lin.spring.notification.dto.NotificationEvent;
import com.lin.spring.notification.publisher.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 营销活动批量发送：直接向 Kafka 流式写入，避免瞬间 INSERT 千万行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignService {

	private final TopicResolver topicResolver;
	private final NotificationEventPublisher eventPublisher;

	/**
	 * 批量发布营销通知事件。
	 *
	 * @param request 活动请求
	 * @return 已接受的消息数量
	 */
	public int sendCampaign(CampaignRequest request) {
		String topic = topicResolver.resolve(request.type());
		String campaignId = request.campaignId() != null ? request.campaignId() : UUID.randomUUID().toString();
		int count = 0;
		for (String userId : request.userIds()) {
			String notificationId = UUID.randomUUID().toString();
			String correlationId = campaignId + ":" + userId;
			NotificationEvent event = new NotificationEvent(
				notificationId,
				userId,
				request.type(),
				request.channel(),
				request.templateId(),
				request.data(),
				campaignId + ":" + userId,
				0,
				correlationId
			);
			eventPublisher.publish(topic, event);
			count++;
		}
		log.info("营销活动已发布 campaignId={} count={} topic={}", campaignId, count, topic);
		return count;
	}
}
