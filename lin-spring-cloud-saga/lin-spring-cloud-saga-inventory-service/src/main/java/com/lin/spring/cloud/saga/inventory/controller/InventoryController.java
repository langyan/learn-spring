package com.lin.spring.cloud.saga.inventory.controller;

import com.lin.spring.cloud.saga.common.dto.InventoryRequest;
import com.lin.spring.cloud.saga.common.dto.InventoryResponse;
import com.lin.spring.cloud.saga.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/reserve")
    public ResponseEntity<InventoryResponse> reserveInventory(@Valid @RequestBody InventoryRequest request) {
        log.info("Received reserve inventory request: {}", request);
        try {
            InventoryResponse response = inventoryService.reserveInventory(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error reserving inventory: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{inventoryId}")
    public ResponseEntity<InventoryResponse> getInventory(@PathVariable String inventoryId) {
        log.info("Received get inventory request: {}", inventoryId);
        try {
            InventoryResponse response = inventoryService.getInventory(inventoryId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting inventory: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<InventoryResponse> getInventoryByOrderId(@PathVariable String orderId) {
        log.info("Received get inventory by order request: {}", orderId);
        try {
            InventoryResponse response = inventoryService.getInventoryByOrderId(orderId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting inventory by order: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{inventoryId}/release")
    public ResponseEntity<Void> releaseInventory(@PathVariable String inventoryId) {
        log.info("Received release inventory request: {}", inventoryId);
        try {
            inventoryService.releaseInventory(inventoryId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error releasing inventory: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{inventoryId}/complete")
    public ResponseEntity<Void> completeInventory(@PathVariable String inventoryId) {
        log.info("Received complete inventory request: {}", inventoryId);
        try {
            inventoryService.completeInventory(inventoryId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error completing inventory: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}