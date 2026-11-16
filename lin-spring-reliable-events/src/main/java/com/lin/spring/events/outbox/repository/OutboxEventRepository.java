package com.lin.spring.events.outbox.repository;

import com.lin.spring.events.outbox.entity.OutboxEvent;
import com.lin.spring.events.outbox.entity.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbox 仓库：派发扫描、条件更新申领、陈旧锁回收与清理。
 * <p>
 * 申领采用「候选集 + 条件批量 UPDATE」而非 SELECT FOR UPDATE：
 * UPDATE 天然原子，多实例并发时只有一个实例能改到行，且不长时间持有数据库锁。
 * </p>
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

	/**
	 * 候选集：到期且待派发的记录（仅取 ID，配合分页限制批量）。
	 */
	@Query("select e.id from OutboxEvent e where e.status = :status and e.availableAt <= :now order by e.id asc")
	List<Long> findCandidateIds(@Param("status") OutboxStatus status, @Param("now") Instant now, Pageable pageable);

	/**
	 * 条件更新申领：只有仍处于 PENDING 且到期的候选才会被置为 PROCESSING 并打上持有者标记。
	 *
	 * @return 实际申领到的条数（并发方申领不到）
	 */
	@Modifying
	@Query("""
		update OutboxEvent e
		   set e.status = com.lin.spring.events.outbox.entity.OutboxStatus.PROCESSING,
		       e.lockOwner = :owner,
		       e.lockedAt = :now,
		       e.attempts = e.attempts + 1
		 where e.id in :ids
		   and e.status = com.lin.spring.events.outbox.entity.OutboxStatus.PENDING
		   and e.availableAt <= :now
		""")
	int claim(@Param("ids") List<Long> ids, @Param("owner") String owner, @Param("now") Instant now);

	/**
	 * 回读本实例申领到的记录。
	 */
	List<OutboxEvent> findByLockOwnerAndStatus(String lockOwner, OutboxStatus status);

	Optional<OutboxEvent> findByEventId(String eventId);

	/**
	 * 陈旧锁回收：持有者申领后长时间未回写（视为崩溃），重置回 PENDING 供其他实例接手。
	 *
	 * @return 回收条数
	 */
	@Modifying
	@Query("""
		update OutboxEvent e
		   set e.status = com.lin.spring.events.outbox.entity.OutboxStatus.PENDING,
		       e.lockOwner = null,
		       e.lockedAt = null,
		       e.lastError = :reason
		 where e.status = com.lin.spring.events.outbox.entity.OutboxStatus.PROCESSING
		   and e.lockedAt < :threshold
		""")
	int recoverStale(@Param("threshold") Instant threshold, @Param("reason") String reason);

	/**
	 * 手动重放：FAILED 记录重置为 PENDING 立即派发。
	 */
	@Modifying
	@Query("""
		update OutboxEvent e
		   set e.status = com.lin.spring.events.outbox.entity.OutboxStatus.PENDING,
		       e.lockOwner = null,
		       e.lockedAt = null,
		       e.attempts = 0,
		       e.availableAt = :now,
		       e.lastError = null
		 where e.id = :id
		   and e.status = com.lin.spring.events.outbox.entity.OutboxStatus.FAILED
		""")
	int replay(@Param("id") Long id, @Param("now") Instant now);

	/**
	 * 按状态 / 关联 ID 分页查询（运维排查）。
	 */
	List<OutboxEvent> findByStatusOrderByCreatedAtDesc(OutboxStatus status, Pageable pageable);

	List<OutboxEvent> findByCorrelationIdOrderByCreatedAtAsc(String correlationId);

	long countByStatus(OutboxStatus status);

	/**
	 * 清理候选：已完结且早于阈值的记录 ID（分批删除避免长事务）。
	 */
	@Query("select e.id from OutboxEvent e where e.status = :done and e.publishedAt < :threshold order by e.id asc")
	List<Long> findCleanupCandidateIds(@Param("done") OutboxStatus done, @Param("threshold") Instant threshold, Pageable pageable);
}
