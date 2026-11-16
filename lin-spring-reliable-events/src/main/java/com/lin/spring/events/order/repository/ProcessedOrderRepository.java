package com.lin.spring.events.order.repository;

import com.lin.spring.events.order.entity.ProcessedOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 消费侧投影仓库：按 eventId 查询用于验证幂等。
 */
public interface ProcessedOrderRepository extends JpaRepository<ProcessedOrder, Long> {

	Optional<ProcessedOrder> findByEventId(String eventId);

	List<ProcessedOrder> findByOrderId(Long orderId);
}
