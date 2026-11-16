package com.lin.spring.orders.order.repository;

import com.lin.spring.orders.order.domain.Order;
import com.lin.spring.orders.order.statemachine.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    Page<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    /**
     * 条件状态转移（乐观）：仅当当前状态在期望集合内才更新。
     * 返回受影响行数（0 = 状态已被并发事件推进，本次事件为过期/乱序，应忽略）。
     * JPQL 更新不会自动递增 @Version，这里显式 version = version + 1。
     */
    @Modifying
    @Query("""
            UPDATE Order o
               SET o.status = :to, o.version = o.version + 1, o.updatedAt = CURRENT_TIMESTAMP
             WHERE o.id = :orderId AND o.status IN :from
            """)
    int transitionIf(@Param("orderId") UUID orderId,
                     @Param("from") Collection<OrderStatus> from,
                     @Param("to") OrderStatus to);
}
