package com.lin.spring.notification.service;

import com.lin.spring.notification.domain.NotificationChannel;
import com.lin.spring.notification.domain.NotificationStatus;
import com.lin.spring.notification.dto.NotificationEvent;
import com.lin.spring.notification.entity.DeliveryLogEntity;
import com.lin.spring.notification.entity.NotificationEntity;
import com.lin.spring.notification.idempotency.NotificationDedupeStore;
import com.lin.spring.notification.provider.NotificationProvider;
import com.lin.spring.notification.provider.ProviderRegistry;
import com.lin.spring.notification.provider.ProviderResult;
import com.lin.spring.notification.repository.DeliveryLogRepository;
import com.lin.spring.notification.repository.NotificationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 通知消费核心逻辑：幂等 → Provider 投递 → 状态更新 → 重试/DLQ。
 */
@Slf4j
@Service
public class NotificationProcessingService {

	private final NotificationDedupeStore dedupeStore;
	private final ProviderRegistry providerRegistry;
	private final NotificationRepository notificationRepository;
	private final DeliveryLogRepository deliveryLogRepository;
	private final RetryService retryService;
	private final Counter sentCounter;
	private final Counter failedCounter;

	public NotificationProcessingService(
		NotificationDedupeStore dedupeStore,
		ProviderRegistry providerRegistry,
		NotificationRepository notificationRepository,
		DeliveryLogRepository deliveryLogRepository,
		RetryService retryService,
		MeterRegistry meterRegistry
	) {
		this.dedupeStore = dedupeStore;
		this.providerRegistry = providerRegistry;
		this.notificationRepository = notificationRepository;
		this.deliveryLogRepository = deliveryLogRepository;
		this.retryService = retryService;
		this.sentCounter = meterRegistry.counter("notification.sent");
		this.failedCounter = meterRegistry.counter("notification.failed");
	}

	/**
	 * 处理单条通知事件（供 Kafka Consumer 或进程内 Publisher 调用）。
	 *
	 * @param event 通知事件
	 */
	public void process(NotificationEvent event) {
		if (!dedupeStore.tryClaim(event.idempotencyKey())) {
			log.debug("跳过重复通知 idempotencyKey={}", event.idempotencyKey());
			return;
		}
		try {
			NotificationProvider provider = providerRegistry.get(event.channel());
			ProviderResult result = provider.send(event);
			if (result.success()) {
				markSent(event, result);
				dedupeStore.markDone(event.idempotencyKey());
				sentCounter.increment();
				return;
			}
			handleFailure(event, result);
		} catch (RuntimeException ex) {
			dedupeStore.release(event.idempotencyKey());
			throw ex;
		}
	}

	private void handleFailure(NotificationEvent event, ProviderResult result) {
		failedCounter.increment();
		saveDeliveryLog(event.notificationId(), result);
		switch (result.failureType()) {
			case RETRYABLE -> {
				dedupeStore.release(event.idempotencyKey());
				retryService.handleRetryableFailure(event);
			}
			case NON_RETRYABLE -> {
				markFailed(event, result);
				dedupeStore.markDone(event.idempotencyKey());
			}
			case null -> markFailed(event, result);
		}
	}

	@Transactional
	protected void markSent(NotificationEvent event, ProviderResult result) {
		updateStatus(event.notificationId(), NotificationStatus.SENT);
		saveDeliveryLog(event.notificationId(), result);
	}

	@Transactional
	protected void markFailed(NotificationEvent event, ProviderResult result) {
		updateStatus(event.notificationId(), NotificationStatus.FAILED);
	}

	private void updateStatus(String notificationId, NotificationStatus status) {
		notificationRepository.findById(notificationId).ifPresent(entity -> {
			entity.setStatus(status);
			entity.setUpdatedAt(Instant.now());
			notificationRepository.save(entity);
		});
	}

	private void saveDeliveryLog(String notificationId, ProviderResult result) {
		String status = result.success() ? "SENT" : "FAILED";
		deliveryLogRepository.save(new DeliveryLogEntity(
			notificationId,
			result.providerName(),
			status,
			result.errorCode()
		));
	}

	/**
	 * 判断事件是否属于指定渠道（Consumer 过滤用）。
	 *
	 * @param event   事件
	 * @param channel 目标渠道
	 * @return 是否匹配
	 */
	public boolean matchesChannel(NotificationEvent event, NotificationChannel channel) {
		return event.channel() == channel;
	}
}
