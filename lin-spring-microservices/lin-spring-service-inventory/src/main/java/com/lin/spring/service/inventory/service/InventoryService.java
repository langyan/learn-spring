package com.lin.spring.service.inventory.service;

import com.lin.spring.service.inventory.dto.InventoryRequest;
import com.lin.spring.service.inventory.dto.InventoryResponse;
import com.lin.spring.service.inventory.dto.StockReservationRequest;
import com.lin.spring.service.inventory.model.Inventory;
import com.lin.spring.service.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryResponse createInventory(InventoryRequest request) {
        if (inventoryRepository.existsByProductCode(request.getProductCode())) {
            throw new IllegalArgumentException("Product already exists");
        }

        Inventory inventory = new Inventory();
        inventory.setProductCode(request.getProductCode());
        inventory.setProductName(request.getProductName());
        inventory.setQuantity(request.getQuantity());
        inventory.setPrice(request.getPrice());
        inventory.setReorderLevel(request.getReorderLevel());

        inventoryRepository.save(inventory);
        log.info("Inventory created for product: {}", request.getProductCode());

        return InventoryResponse.fromEntity(inventory);
    }

    public InventoryResponse getInventoryByProductCode(String productCode) {
        Inventory inventory = inventoryRepository.findByProductCode(productCode)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        return InventoryResponse.fromEntity(inventory);
    }

    public List<InventoryResponse> getAllInventory() {
        return inventoryRepository.findAll().stream()
                .map(InventoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public boolean reserveStock(StockReservationRequest request) {
        log.info("Reserving stock for product: {}, quantity: {}", request.getProductCode(), request.getQuantity());

        Inventory inventory = inventoryRepository.findByProductCode(request.getProductCode())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (inventory.getAvailableQuantity() < request.getQuantity()) {
            log.warn("Insufficient stock for product: {}", request.getProductCode());
            return false;
        }

        inventory.setReservedQuantity(inventory.getReservedQuantity() + request.getQuantity());
        inventoryRepository.save(inventory);

        log.info("Stock reserved for product: {}", request.getProductCode());
        return true;
    }

    @Transactional
    public void confirmReservation(String productCode, Integer quantity) {
        log.info("Confirming reservation for product: {}, quantity: {}", productCode, quantity);

        Inventory inventory = inventoryRepository.findByProductCode(productCode)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        inventory.setQuantity(inventory.getQuantity() - quantity);
        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        inventoryRepository.save(inventory);

        log.info("Reservation confirmed for product: {}", productCode);
    }

    @Transactional
    public void releaseReservation(String productCode, Integer quantity) {
        log.info("Releasing reservation for product: {}, quantity: {}", productCode, quantity);

        Inventory inventory = inventoryRepository.findByProductCode(productCode)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        inventory.setReservedQuantity(inventory.getReservedQuantity() - quantity);
        inventoryRepository.save(inventory);

        log.info("Reservation released for product: {}", productCode);
    }

    public InventoryResponse updateStock(String productCode, Integer quantity) {
        Inventory inventory = inventoryRepository.findByProductCode(productCode)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        inventory.setQuantity(quantity);
        inventoryRepository.save(inventory);

        log.info("Stock updated for product: {}", productCode);
        return InventoryResponse.fromEntity(inventory);
    }
}
