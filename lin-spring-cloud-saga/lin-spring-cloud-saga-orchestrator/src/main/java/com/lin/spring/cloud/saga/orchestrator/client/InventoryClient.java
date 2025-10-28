package com.lin.spring.cloud.saga.orchestrator.client;

import com.lin.spring.cloud.saga.common.dto.InventoryRequest;
import com.lin.spring.cloud.saga.common.dto.InventoryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "inventory-service")
public interface InventoryClient {

    @PostMapping("/api/inventory/reserve")
    InventoryResponse reserveInventory(@RequestBody InventoryRequest request);

    @GetMapping("/api/inventory/{inventoryId}")
    InventoryResponse getInventory(@PathVariable("inventoryId") String inventoryId);

    @GetMapping("/api/inventory/order/{orderId}")
    InventoryResponse getInventoryByOrderId(@PathVariable("orderId") String orderId);

    @PostMapping("/api/inventory/{inventoryId}/release")
    void releaseInventory(@PathVariable("inventoryId") String inventoryId);

    @PostMapping("/api/inventory/{inventoryId}/complete")
    void completeInventory(@PathVariable("inventoryId") String inventoryId);
}