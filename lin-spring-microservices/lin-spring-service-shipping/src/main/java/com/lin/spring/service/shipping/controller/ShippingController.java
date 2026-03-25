package com.lin.spring.service.shipping.controller;

import com.lin.spring.service.shipping.dto.ShippingRequest;
import com.lin.spring.service.shipping.dto.ShippingResponse;
import com.lin.spring.service.shipping.model.Shipment;
import com.lin.spring.service.shipping.service.ShippingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
@Slf4j
public class ShippingController {

    private final ShippingService shippingService;

    @PostMapping
    public ResponseEntity<ShippingResponse> createShipment(@Valid @RequestBody ShippingRequest request) {
        return ResponseEntity.ok(shippingService.createShipment(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShippingResponse> getShipmentById(@PathVariable Long id) {
        return ResponseEntity.ok(shippingService.getShipmentById(id));
    }

    @GetMapping("/tracking/{trackingNumber}")
    public ResponseEntity<ShippingResponse> getShipmentByTrackingNumber(@PathVariable String trackingNumber) {
        return ResponseEntity.ok(shippingService.getShipmentByTrackingNumber(trackingNumber));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ShippingResponse> getShipmentByOrderId(@PathVariable Long orderId) {
        return ResponseEntity.ok(shippingService.getShipmentByOrderId(orderId));
    }

    @GetMapping
    public ResponseEntity<List<ShippingResponse>> getAllShipments() {
        return ResponseEntity.ok(shippingService.getAllShipments());
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<ShippingResponse> updateShipmentStatus(
            @PathVariable Long id,
            @RequestParam Shipment.ShipmentStatus status) {
        return ResponseEntity.ok(shippingService.updateShipmentStatus(id, status));
    }

    @PostMapping("/order/{orderId}/ship")
    public ResponseEntity<ShippingResponse> shipOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(shippingService.shipOrder(orderId));
    }
}
