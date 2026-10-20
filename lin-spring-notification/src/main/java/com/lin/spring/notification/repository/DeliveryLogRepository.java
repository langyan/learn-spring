package com.lin.spring.notification.repository;

import com.lin.spring.notification.entity.DeliveryLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 投递日志数据访问。
 */
public interface DeliveryLogRepository extends JpaRepository<DeliveryLogEntity, Long> {
}
