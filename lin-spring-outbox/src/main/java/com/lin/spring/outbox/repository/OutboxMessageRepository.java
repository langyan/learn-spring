package com.lin.spring.outbox.repository;

import com.lin.spring.outbox.entity.OutboxMessage;
import com.lin.spring.outbox.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Outbox 表数据访问。
 */
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

	/**
	 * 按状态与创建时间升序获取待处理记录。
	 */
	List<OutboxMessage> findByStatusOrderByIdAsc(OutboxStatus status);

	/**
	 * 按事件 ID 查找（幂等去重可用）。
	 */
	Optional<OutboxMessage> findByEventId(String eventId);

	long countByStatus(OutboxStatus status);
}
