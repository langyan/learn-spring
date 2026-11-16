package com.lin.spring.orders.inventory.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<com.lin.spring.orders.inventory.domain.InventoryItem, Long> {

    /**
     * 原子条件预留（文章第 9 节防超卖核心）：
     * 单条 SQL 依赖行级锁完成"读-判-减"，WHERE 条件保证绝不减到负数；
     * 返回 0 = 库存不足（调用方必须校验受影响行数）。
     */
    @Modifying
    @Query(value = """
            UPDATE inventory
               SET available_quantity = available_quantity - :qty,
                   reserved_quantity = reserved_quantity + :qty
             WHERE product_id = :productId
               AND available_quantity >= :qty
            """, nativeQuery = true)
    int tryReserve(@Param("productId") long productId, @Param("qty") int qty);

    /** 确认预留：available 已在预留时扣减，这里只结转 reserved。 */
    @Modifying
    @Query(value = """
            UPDATE inventory
               SET reserved_quantity = reserved_quantity - :qty
             WHERE product_id = :productId
               AND reserved_quantity >= :qty
            """, nativeQuery = true)
    int confirmReserved(@Param("productId") long productId, @Param("qty") int qty);

    /** 释放预留：库存加回 available，reserved 结转。 */
    @Modifying
    @Query(value = """
            UPDATE inventory
               SET available_quantity = available_quantity + :qty,
                   reserved_quantity = reserved_quantity - :qty
             WHERE product_id = :productId
               AND reserved_quantity >= :qty
            """, nativeQuery = true)
    int releaseReserved(@Param("productId") long productId, @Param("qty") int qty);

    /** 补货（演示用管理端点）。 */
    @Modifying
    @Query(value = """
            UPDATE inventory SET available_quantity = available_quantity + :qty
             WHERE product_id = :productId
            """, nativeQuery = true)
    int restock(@Param("productId") long productId, @Param("qty") int qty);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.productId = :productId")
    Optional<com.lin.spring.orders.inventory.domain.InventoryItem> findByProductIdForUpdate(@Param("productId") long productId);
}
