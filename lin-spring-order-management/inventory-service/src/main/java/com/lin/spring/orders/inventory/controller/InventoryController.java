package com.lin.spring.orders.inventory.controller;

import com.lin.spring.orders.inventory.domain.InventoryItem;
import com.lin.spring.orders.inventory.repository.InventoryRepository;
import com.lin.spring.orders.inventory.service.ProductCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 库存观察/管理端点（演示用）：查看权威库存、补货以重复跑防超卖演示。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryRepository inventoryRepository;
    private final ProductCatalogService catalogService;

    @GetMapping("/inventory/{productId}")
    public Map<String, Object> stock(@PathVariable long productId) {
        InventoryItem item = inventoryRepository.findById(productId)
                .orElseThrow(() -> new ProductCatalogService.ProductNotFoundException(productId));
        return Map.of(
                "productId", productId,
                "availableQuantity", item.getAvailableQuantity(),
                "reservedQuantity", item.getReservedQuantity());
    }

    public record RestockRequest(Long productId, Integer quantity) {
    }

    @PostMapping("/admin/restock")
    public Map<String, Object> restock(@RequestBody RestockRequest request) {
        int updated = catalogService.restock(request.productId(), request.quantity());
        return Map.of("productId", request.productId(),
                "restocked", updated == 1,
                "addedQuantity", request.quantity());
    }
}
