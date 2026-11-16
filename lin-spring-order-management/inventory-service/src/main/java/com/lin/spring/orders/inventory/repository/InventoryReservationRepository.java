package com.lin.spring.orders.inventory.repository;

import com.lin.spring.orders.inventory.domain.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {

    List<InventoryReservation> findByOrderId(UUID orderId);

    List<InventoryReservation> findByOrderIdAndStatus(UUID orderId, InventoryReservation.Status status);

    /**
     * 行级条件转移：仅 RESERVED 行转为 RELEASED。
     * 返回 0（已被并发路径释放/确认）时调用方不得重复回补库存——释放因此天然幂等。
     */
    @Modifying
    @Query("""
            UPDATE InventoryReservation r
               SET r.status = 'RELEASED', r.updatedAt = CURRENT_TIMESTAMP
             WHERE r.id = :id AND r.status = 'RESERVED'
            """)
    int releaseIfReserved(@Param("id") UUID id);

    @Modifying
    @Query("""
            UPDATE InventoryReservation r
               SET r.status = 'CONFIRMED', r.updatedAt = CURRENT_TIMESTAMP
             WHERE r.id = :id AND r.status = 'RESERVED'
            """)
    int confirmIfReserved(@Param("id") UUID id);

    /** 预留过期扫描（部分索引 idx_reservations_expiry 支撑）。 */
    @Query("""
            SELECT r FROM InventoryReservation r
             WHERE r.status = 'RESERVED' AND r.expiresAt < :now
            """)
    List<InventoryReservation> findExpired(@Param("now") Instant now);
}
