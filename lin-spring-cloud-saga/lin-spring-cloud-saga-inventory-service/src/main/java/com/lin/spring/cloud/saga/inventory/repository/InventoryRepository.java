package com.lin.spring.cloud.saga.inventory.repository;

import com.lin.spring.cloud.saga.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByInventoryId(String inventoryId);
    Optional<Inventory> findByOrderId(String orderId);
    boolean existsByInventoryId(String inventoryId);
}