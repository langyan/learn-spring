package com.lin.spring.cloud.saga.inventory.service;

import com.lin.spring.cloud.saga.common.dto.InventoryRequest;
import com.lin.spring.cloud.saga.common.dto.InventoryResponse;
import com.lin.spring.cloud.saga.common.enums.InventoryStatus;
import com.lin.spring.cloud.saga.inventory.entity.Inventory;
import com.lin.spring.cloud.saga.inventory.entity.ProductStock;
import com.lin.spring.cloud.saga.inventory.repository.InventoryRepository;
import com.lin.spring.cloud.saga.inventory.repository.ProductStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductStockRepository productStockRepository;

    @Transactional
    public InventoryResponse reserveInventory(InventoryRequest request) {
        log.info("Reserving inventory for order: {}, product: {}", request.getOrderId(), request.getProductId());

        // 检查库存
        ProductStock productStock = productStockRepository.findByProductId(request.getProductId())
                .orElseGet(() -> {
                    ProductStock newStock = new ProductStock();
                    newStock.setProductId(request.getProductId());
                    return newStock;
                });

        int availableStock = productStock.getStockQuantity() - productStock.getReservedQuantity();
        if (availableStock < request.getQuantity()) {
            throw new RuntimeException("Insufficient stock for product: " + request.getProductId() +
                    ". Available: " + availableStock + ", Requested: " + request.getQuantity());
        }

        // 模拟库存预留失败的概率（5%）
        if (Math.random() < 0.05) {
            throw new RuntimeException("Inventory reservation failed randomly");
        }

        // 预留库存
        productStock.setReservedQuantity(productStock.getReservedQuantity() + request.getQuantity());
        productStockRepository.save(productStock);

        // 创建库存记录
        Inventory inventory = new Inventory();
        inventory.setOrderId(request.getOrderId());
        inventory.setProductId(request.getProductId());
        inventory.setQuantity(request.getQuantity());
        inventory.setStatus(InventoryStatus.RESERVED);

        Inventory savedInventory = inventoryRepository.save(inventory);
        log.info("Inventory reserved successfully: {}", savedInventory.getInventoryId());

        return convertToResponse(savedInventory);
    }

    @Transactional
    public InventoryResponse getInventory(String inventoryId) {
        log.info("Getting inventory: {}", inventoryId);

        Optional<Inventory> inventory = inventoryRepository.findByInventoryId(inventoryId);
        if (inventory.isEmpty()) {
            throw new RuntimeException("Inventory not found: " + inventoryId);
        }

        return convertToResponse(inventory.get());
    }

    @Transactional
    public InventoryResponse getInventoryByOrderId(String orderId) {
        log.info("Getting inventory by order: {}", orderId);

        Optional<Inventory> inventory = inventoryRepository.findByOrderId(orderId);
        if (inventory.isEmpty()) {
            throw new RuntimeException("Inventory not found for order: " + orderId);
        }

        return convertToResponse(inventory.get());
    }

    @Transactional
    public void releaseInventory(String inventoryId) {
        log.info("Releasing inventory: {}", inventoryId);

        Optional<Inventory> inventory = inventoryRepository.findByInventoryId(inventoryId);
        if (inventory.isEmpty()) {
            throw new RuntimeException("Inventory not found: " + inventoryId);
        }

        Inventory inventoryEntity = inventory.get();
        inventoryEntity.setStatus(InventoryStatus.RELEASED);
        inventoryRepository.save(inventoryEntity);

        // 释放预留库存
        ProductStock productStock = productStockRepository.findByProductId(inventoryEntity.getProductId())
                .orElseThrow(() -> new RuntimeException("Product stock not found: " + inventoryEntity.getProductId()));

        productStock.setReservedQuantity(productStock.getReservedQuantity() - inventoryEntity.getQuantity());
        productStockRepository.save(productStock);

        log.info("Inventory released successfully: {}", inventoryId);
    }

    @Transactional
    public void completeInventory(String inventoryId) {
        log.info("Completing inventory: {}", inventoryId);

        Optional<Inventory> inventory = inventoryRepository.findByInventoryId(inventoryId);
        if (inventory.isEmpty()) {
            throw new RuntimeException("Inventory not found: " + inventoryId);
        }

        Inventory inventoryEntity = inventory.get();
        inventoryEntity.setStatus(InventoryStatus.COMPLETED);
        inventoryRepository.save(inventoryEntity);

        // 扣减实际库存
        ProductStock productStock = productStockRepository.findByProductId(inventoryEntity.getProductId())
                .orElseThrow(() -> new RuntimeException("Product stock not found: " + inventoryEntity.getProductId()));

        productStock.setStockQuantity(productStock.getStockQuantity() - inventoryEntity.getQuantity());
        productStock.setReservedQuantity(productStock.getReservedQuantity() - inventoryEntity.getQuantity());
        productStockRepository.save(productStock);

        log.info("Inventory completed successfully: {}", inventoryId);
    }

    private InventoryResponse convertToResponse(Inventory inventory) {
        return new InventoryResponse(
            inventory.getInventoryId(),
            inventory.getOrderId(),
            inventory.getProductId(),
            inventory.getQuantity(),
            inventory.getStatus().name(),
            inventory.getCreatedAt()
        );
    }
}