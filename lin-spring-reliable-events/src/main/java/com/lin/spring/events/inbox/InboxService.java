package com.lin.spring.events.inbox;

import com.lin.spring.events.config.EventsProperties;
import com.lin.spring.events.inbox.entity.InboxEvent;
import com.lin.spring.events.inbox.entity.InboxStatus;
import com.lin.spring.events.inbox.repository.InboxEventRepository;
import com.lin.spring.events.observability.EventsMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收件箱服务：登记（幂等去重）→ 申领 → 处理 → 回写。
 * <p>
 * 去重依赖 event_id 唯一约束：先查后插是快路径，并发下的漏网之鱼由
 * {@link DataIntegrityViolationException} 兜底判 DUPLICATE，规避 check-then-act 竞态。
 * </p>
 */
@Slf4j
@Service
public class InboxService {

	/** 登记结果。 */
	public enum ReceiveResult { NEW, DUPLICATE }

	private final InboxEventRepository inboxEventRepository;

	private final InboxMarkService markService;

	private final EventsProperties properties;

	private final EventsMetrics metrics;

	private final Map<String, InboxHandler> handlers;

	/** 申领（@Modifying 条件更新）需要显式事务；用编程式事务避免把 handler 包进长事务。 */
	private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

	public InboxService(InboxEventRepository inboxEventRepository,
						InboxMarkService markService,
						EventsProperties properties,
						EventsMetrics metrics,
						org.springframework.transaction.support.TransactionTemplate transactionTemplate,
						@Lazy List<InboxHandler> handlerList) {
		this.inboxEventRepository = inboxEventRepository;
		this.markService = markService;
		this.properties = properties;
		this.metrics = metrics;
		this.transactionTemplate = transactionTemplate;
		this.handlers = handlerList.stream()
			.collect(Collectors.toMap(InboxHandler::eventType, Function.identity()));
	}

	/**
	 * 登记事件（幂等）并立即尝试处理一次。
	 *
	 * @return DUPLICATE 表示重复投递被去重；NEW 表示首次接收并已尝试处理
	 */
	public ReceiveResult receive(String eventId, String eventType, String source,
								 String correlationId, String payload) {
		boolean duplicate = register(eventId, eventType, source, correlationId, payload);
		if (duplicate) {
			metrics.inboxDuplicate();
			log.info("Inbox duplicate delivery deduplicated: eventId={}", eventId);
			return ReceiveResult.DUPLICATE;
		}
		log.info("Inbox received event: eventId={} type={} correlationId={}", eventId, eventType, correlationId);
		// 立即尝试处理一次（失败不抛出，进入重试队列）
		processDueEvents();
		return ReceiveResult.NEW;
	}

	/**
	 * 登记去重：先查后插 + 唯一约束兜底。
	 *
	 * @return true 表示重复
	 */
	boolean register(String eventId, String eventType, String source, String correlationId, String payload) {
		if (inboxEventRepository.existsByEventId(eventId)) {
			return true;
		}
		try {
			inboxEventRepository.save(new InboxEvent(eventId, eventType, source, correlationId, payload));
			return false;
		} catch (DataIntegrityViolationException e) {
			// 并发重复投递：唯一约束兜底
			return true;
		}
	}

	/**
	 * 申领并处理一批到期事件（重试调度器与 receive 内联调用）。
	 *
	 * @return 处理条数
	 */
	public int processDueEvents() {
		Instant now = Instant.now();
		int batchSize = properties.getInbox().getBatchSize();
		List<Long> candidates = inboxEventRepository
			.findCandidateIds(InboxStatus.RECEIVED, now, PageRequest.of(0, batchSize));
		if (candidates.isEmpty()) {
			return 0;
		}
		String owner = properties.getInstanceId();
		// 申领是 @Modifying 条件更新，必须在事务内提交；handler 保持在事务外执行
		Integer claimed = transactionTemplate.execute(
			tx -> inboxEventRepository.claim(candidates, owner, now));
		if (claimed == null || claimed == 0) {
			return 0;
		}
		List<InboxEvent> mine = inboxEventRepository.findByLockOwnerAndStatus(owner, InboxStatus.PROCESSING);
		for (InboxEvent event : mine) {
			processOne(event);
		}
		return mine.size();
	}

	/**
	 * 处理单条（已申领）：定位 handler → 执行（事务外）→ 回写结果。
	 */
	void processOne(InboxEvent event) {
		InboxHandler handler = handlers.get(event.getEventType());
		if (handler == null) {
			metrics.inboxFailed();
			markService.markFailed(event, new IllegalStateException(
				"no InboxHandler registered for eventType=" + event.getEventType()));
			return;
		}
		try {
			handler.handle(event);
			markService.markProcessed(event);
			metrics.inboxProcessed();
			log.debug("Inbox processed: eventId={} attempts={}", event.getEventId(), event.getAttempts());
		} catch (Exception e) {
			metrics.inboxFailed();
			boolean exhausted = markService.markFailed(event, e);
			if (!exhausted) {
				log.warn("Inbox handler failed: eventId={} attempts={}/{} err={}",
					event.getEventId(), event.getAttempts(), properties.getInbox().getMaxAttempts(), e.getMessage());
			}
		}
	}

	/**
	 * 手动重放 FAILED 事件。
	 *
	 * @return true 表示重放成功入队
	 */
	@Transactional
	public boolean replay(String eventId) {
		return inboxEventRepository.replay(eventId, Instant.now()) > 0;
	}

	/**
	 * 幂等验证辅助：按 eventId 查询。
	 */
	public InboxEvent findByEventId(String eventId) {
		return inboxEventRepository.findByEventId(eventId).orElse(null);
	}
}
