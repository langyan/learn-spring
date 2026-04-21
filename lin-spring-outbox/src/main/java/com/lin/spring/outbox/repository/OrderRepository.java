package com.lin.spring.outbox.repository;

import com.lin.spring.outbox.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 订单数据访问。
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
}
