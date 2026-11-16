package com.lin.spring.events.order.repository;

import com.lin.spring.events.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 订单仓库。
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
}
