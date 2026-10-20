package com.lin.spring.notification.repository;

import com.lin.spring.notification.domain.NotificationStatus;
import com.lin.spring.notification.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 通知主表数据访问。
 */
public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {

	Optional<NotificationEntity> findByIdempotencyKey(String idempotencyKey);

	/**
	 * 查询已到期的定时通知。
	 */
	@Query("""
		SELECT n FROM NotificationEntity n
		WHERE n.status = :status AND n.scheduledAt <= :now
		ORDER BY n.scheduledAt ASC
		""")
	List<NotificationEntity> findDueScheduled(
		@Param("status") NotificationStatus status,
		@Param("now") Instant now
	);
}
