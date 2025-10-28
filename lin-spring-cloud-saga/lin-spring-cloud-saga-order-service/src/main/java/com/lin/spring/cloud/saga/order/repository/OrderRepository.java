package com.lin.spring.cloud.saga.order.repository;

import com.lin.spring.cloud.saga.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderId(String orderId);
    boolean existsByOrderId(String orderId);
}