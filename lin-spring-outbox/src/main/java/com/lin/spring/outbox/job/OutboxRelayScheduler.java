package com.lin.spring.outbox.job;

import com.lin.spring.outbox.service.OutboxRelayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时触发 Outbox 中继（Polling Publisher）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

	private final OutboxRelayService outboxRelayService;

	/**
	 * 按固定间隔拉取待投递 Outbox。
	 */
	@Scheduled(fixedDelayString = "${app.outbox.relay.fixed-delay-ms:2000}")
	public void tick() {
		int processed = outboxRelayService.processPendingBatch();
		if (processed > 0) {
			log.debug("Outbox relay tick processed {} message(s)", processed);
		}
	}
}
