package com.lin.spring.events.outbox;

import com.lin.spring.events.outbox.entity.OutboxEvent;
import com.lin.spring.events.outbox.entity.OutboxStatus;
import com.lin.spring.events.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 申领服务：事务内完成「候选 → 条件更新 → 回读」三步。
 * <p>
 * 条件 UPDATE 原子地完成抢占：并发实例对同一批候选只有一方能改到行（多实例安全）；
 * 申领事务立即提交，随后的发布 IO 在事务外执行，不会长时间占用数据库连接。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxClaimService {

	private final OutboxEventRepository outboxEventRepository;

	/**
	 * 申领一批到期事件。
	 *
	 * @param owner     本实例标识（写入 lock_owner）
	 * @param batchSize 最多申领条数
	 * @return 本实例成功申领的事件列表
	 */
	@Transactional
	public List<OutboxEvent> claimBatch(String owner, int batchSize) {
		Instant now = Instant.now();
		List<Long> candidates = outboxEventRepository
			.findCandidateIds(OutboxStatus.PENDING, now, PageRequest.of(0, batchSize));
		if (candidates.isEmpty()) {
			return List.of();
		}
		int claimed = outboxEventRepository.claim(candidates, owner, now);
		if (claimed == 0) {
			// 候选被其他实例抢先，正常现象
			log.debug("Outbox claim lost race: candidates={} owner={}", candidates.size(), owner);
			return List.of();
		}
		List<OutboxEvent> mine = outboxEventRepository.findByLockOwnerAndStatus(owner, OutboxStatus.PROCESSING);
		log.debug("Outbox claimed {}/{} event(s) for owner={}", mine.size(), candidates.size(), owner);
		return mine;
	}
}
