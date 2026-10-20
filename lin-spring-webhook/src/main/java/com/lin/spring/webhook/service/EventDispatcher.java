package com.lin.spring.webhook.service;

import com.lin.spring.webhook.config.WebhookProperties;
import com.lin.spring.webhook.domain.DeliveryStatus;
import com.lin.spring.webhook.domain.MerchantLane;
import com.lin.spring.webhook.dto.DeliveryJob;
import com.lin.spring.webhook.dto.WebhookEvent;
import com.lin.spring.webhook.entity.WebhookEndpointEntity;
import com.lin.spring.webhook.isolation.MerchantLaneRouter;
import com.lin.spring.webhook.queue.DeliveryQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 事件消费主体：查端点 → 建任务 → 按泳道入队。
 *
 * <p>Kafka 消费者与进程内总线都调用这里，保证两条链路行为一致。</p>
 */
@Slf4j
@Service
public class EventDispatcher {

	private final EndpointRegistryService endpointRegistry;
	private final MerchantLaneRouter laneRouter;
	private final DeliveryQueue queue;
	private final DeliveryLogService deliveryLogService;
	private final WebhookProperties properties;

	public EventDispatcher(EndpointRegistryService endpointRegistry,
						   MerchantLaneRouter laneRouter,
						   DeliveryQueue queue,
						   DeliveryLogService deliveryLogService,
						   WebhookProperties properties) {
		this.endpointRegistry = endpointRegistry;
		this.laneRouter = laneRouter;
		this.queue = queue;
		this.deliveryLogService = deliveryLogService;
		this.properties = properties;
	}

	/**
	 * 把一个内部事件展开成若干投递任务并入队。
	 *
	 * @param event 内部事件
	 */
	public void dispatch(WebhookEvent event) {
		List<WebhookEndpointEntity> endpoints = endpointRegistry.activeEndpoints(event.merchantId());
		if (endpoints.isEmpty()) {
			log.debug("商户 {} 没有可用端点，事件 {} 无需投递", event.merchantId(), event.eventId());
			return;
		}
		for (WebhookEndpointEntity endpoint : endpoints) {
			if (!endpoint.subscribes(event.eventType())) {
				continue;
			}
			DeliveryJob job = DeliveryJob.first(
				event.eventId(), event.merchantId(), endpoint.getEndpointId(), endpoint.getUrl(),
				endpoint.getSecret(), event.eventType(), event.payload(), event.createdAt());

			MerchantLane lane = laneRouter.route(endpoint.getEndpointId());
			if (lane == MerchantLane.QUARANTINE) {
				// 目的地已判定为彻底离线，直接取消，不消耗任何投递资源
				deliveryLogService.markStatus(event.eventId(), DeliveryStatus.CANCELLED,
					"目的地离线超过阈值，投递已取消", null);
				log.warn("事件 {} 因目的地 {} 长期离线被取消", event.eventId(), endpoint.getEndpointId());
				continue;
			}
			if (lane == MerchantLane.SLOW) {
				queue.pushDelayed(job, properties.getCircuit().getWaitDurationInOpenStateSeconds());
			} else {
				queue.pushReady(job);
			}
		}
	}

	/**
	 * 入队前的即时性检查：返回 true 表示主队列当前完全空闲（便于测试断言）。
	 */
	public boolean isIdle() {
		return queue.readyDepth() == 0 && queue.delayedDepth() == 0;
	}
}
