package com.lin.spring.service.inventory.controller;

import com.lin.spring.service.inventory.dto.InventoryRequest;
import com.lin.spring.service.inventory.dto.InventoryResponse;
import com.lin.spring.service.inventory.dto.StockReservationRequest;
import com.lin.spring.service.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping
    public ResponseEntity<InventoryResponse> createInventory(@Valid @RequestBody InventoryRequest request) {
        return ResponseEntity.ok(inventoryService.createInventory(request));
    }

    @GetMapping
    public ResponseEntity<List<InventoryResponse>> getAllInventory() {
        return ResponseEntity.ok(inventoryService.getAllInventory());
    }

    @GetMapping("/product/{productCode}")
    public ResponseEntity<InventoryResponse> getInventoryByProductCode(@PathVariable String productCode) {
        return ResponseEntity.ok(inventoryService.getInventoryByProductCode(productCode));
    }

    @PostMapping("/reserve")
    public ResponseEntity<Boolean> reserveStock(@Valid @RequestBody StockReservationRequest request) {
        return ResponseEntity.ok(inventoryService.reserveStock(request));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Void> confirmReservation(@RequestParam String productCode, @RequestParam Integer quantity) {
        inventoryService.confirmReservation(productCode, quantity);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/release")
    public ResponseEntity<Void> releaseReservation(@RequestParam String productCode, @RequestParam Integer quantity) {
        inventoryService.releaseReservation(productCode, quantity);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/product/{productCode}/stock")
    public ResponseEntity<InventoryResponse> updateStock(
            @PathVariable String productCode,
            @RequestParam Integer quantity) {
        return ResponseEntity.ok(inventoryService.updateStock(productCode, quantity));
    }
}
