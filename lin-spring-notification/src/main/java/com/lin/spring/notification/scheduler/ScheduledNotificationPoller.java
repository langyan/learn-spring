package com.lin.spring.notification.scheduler;

import com.lin.spring.notification.config.NotificationProperties;
import com.lin.spring.notification.domain.NotificationStatus;
import com.lin.spring.notification.entity.NotificationEntity;
import com.lin.spring.notification.repository.NotificationRepository;
import com.lin.spring.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 定时通知调度器：扫描已到期的 SCHEDULED 通知并发布到 Kafka。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledNotificationPoller {

	private final NotificationRepository notificationRepository;
	private final NotificationService notificationService;
	private final NotificationProperties properties;

	/**
	 * 轮询到期定时通知。
	 */
	@Scheduled(fixedDelayString = "${app.notification.scheduler.fixed-delay-ms:5000}")
	public void pollDueNotifications() {
		List<NotificationEntity> due = notificationRepository.findDueScheduled(
			NotificationStatus.SCHEDULED,
			Instant.now()
		);
		if (due.isEmpty()) {
			return;
		}
		log.info("发现 {} 条到期定时通知", due.size());
		for (NotificationEntity entity : due) {
			try {
				notificationService.publishScheduled(entity);
			} catch (RuntimeException ex) {
				log.error("定时通知发布失败 id={}", entity.getId(), ex);
			}
		}
	}
}
