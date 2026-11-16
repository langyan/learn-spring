package com.lin.spring.events.inbox.repository;

import com.lin.spring.events.inbox.entity.InboxEvent;
import com.lin.spring.events.inbox.entity.InboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Inbox 仓库：登记去重、条件更新申领、陈旧锁回收、重放与清理。
 */
public interface InboxEventRepository extends JpaRepository<InboxEvent, Long> {

	boolean existsByEventId(String eventId);

	Optional<InboxEvent> findByEventId(String eventId);

	/**
	 * 候选集：到期且待处理的记录 ID。
	 */
	@Query("select e.id from InboxEvent e where e.status = :status and e.availableAt <= :now order by e.id asc")
	List<Long> findCandidateIds(@Param("status") InboxStatus status, @Param("now") Instant now, Pageable pageable);

	/**
	 * 条件更新申领：RECEIVED 且到期 → PROCESSING 并打锁。
	 */
	@Modifying
	@Query("""
		update InboxEvent e
		   set e.status = com.lin.spring.events.inbox.entity.InboxStatus.PROCESSING,
		       e.lockOwner = :owner,
		       e.lockedAt = :now,
		       e.attempts = e.attempts + 1
		 where e.id in :ids
		   and e.status = com.lin.spring.events.inbox.entity.InboxStatus.RECEIVED
		   and e.availableAt <= :now
		""")
	int claim(@Param("ids") List<Long> ids, @Param("owner") String owner, @Param("now") Instant now);

	List<InboxEvent> findByLockOwnerAndStatus(String lockOwner, InboxStatus status);

	/**
	 * 陈旧锁回收：PROCESSING 超时 → 回到 RECEIVED。
	 */
	@Modifying
	@Query("""
		update InboxEvent e
		   set e.status = com.lin.spring.events.inbox.entity.InboxStatus.RECEIVED,
		       e.lockOwner = null,
		       e.lockedAt = null,
		       e.lastError = :reason
		 where e.status = com.lin.spring.events.inbox.entity.InboxStatus.PROCESSING
		   and e.lockedAt < :threshold
		""")
	int recoverStale(@Param("threshold") Instant threshold, @Param("reason") String reason);

	/**
	 * 手动重放：FAILED → RECEIVED 立即重试。
	 */
	@Modifying
	@Query("""
		update InboxEvent e
		   set e.status = com.lin.spring.events.inbox.entity.InboxStatus.RECEIVED,
		       e.lockOwner = null,
		       e.lockedAt = null,
		       e.attempts = 0,
		       e.availableAt = :now,
		       e.lastError = null
		 where e.eventId = :eventId
		   and e.status = com.lin.spring.events.inbox.entity.InboxStatus.FAILED
		""")
	int replay(@Param("eventId") String eventId, @Param("now") Instant now);

	List<InboxEvent> findByStatusOrderByReceivedAtDesc(InboxStatus status, Pageable pageable);

	List<InboxEvent> findByCorrelationIdOrderByReceivedAtAsc(String correlationId);

	long countByStatus(InboxStatus status);

	/**
	 * 清理候选：已完结且早于阈值的记录 ID。
	 */
	@Query("select e.id from InboxEvent e where e.status = :done and e.processedAt < :threshold order by e.id asc")
	List<Long> findCleanupCandidateIds(@Param("done") InboxStatus done, @Param("threshold") Instant threshold, Pageable pageable);
}
