package com.lin.spring.service.shipping.service;

import com.lin.spring.service.shipping.dto.ShippingRequest;
import com.lin.spring.service.shipping.dto.ShippingResponse;
import com.lin.spring.service.shipping.model.Shipment;
import com.lin.spring.service.shipping.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingService {

    private final ShipmentRepository shipmentRepository;

    public ShippingResponse createShipment(ShippingRequest request) {
        log.info("Creating shipment for order: {}", request.getOrderId());

        Shipment shipment = new Shipment();
        shipment.setOrderId(request.getOrderId());
        shipment.setRecipientName(request.getRecipientName());
        shipment.setRecipientAddress(request.getRecipientAddress());
        shipment.setRecipientCity(request.getRecipientCity());
        shipment.setRecipientPostalCode(request.getRecipientPostalCode());
        shipment.setRecipientCountry(request.getRecipientCountry());
        shipment.setCarrier(request.getCarrier() != null ? request.getCarrier() : "Standard Shipping");
        shipment.setStatus(Shipment.ShipmentStatus.PROCESSING);

        // Estimate delivery (3-5 business days)
        shipment.setEstimatedDeliveryDate(LocalDateTime.now().plusDays(5));

        shipmentRepository.save(shipment);
        log.info("Shipment created with tracking number: {}", shipment.getTrackingNumber());

        return ShippingResponse.fromEntity(shipment);
    }

    public ShippingResponse getShipmentById(Long id) {
        Shipment shipment = shipmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Shipment not found"));
        return ShippingResponse.fromEntity(shipment);
    }

    public ShippingResponse getShipmentByTrackingNumber(String trackingNumber) {
        Shipment shipment = shipmentRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new RuntimeException("Shipment not found"));
        return ShippingResponse.fromEntity(shipment);
    }

    public ShippingResponse getShipmentByOrderId(Long orderId) {
        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Shipment not found"));
        return ShippingResponse.fromEntity(shipment);
    }

    public List<ShippingResponse> getAllShipments() {
        return shipmentRepository.findAll().stream()
                .map(ShippingResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public ShippingResponse updateShipmentStatus(Long id, Shipment.ShipmentStatus status) {
        log.info("Updating shipment status to: {} for shipment: {}", status, id);

        Shipment shipment = shipmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Shipment not found"));

        shipment.setStatus(status);

        if (status == Shipment.ShipmentStatus.SHIPPED) {
            shipment.setStatus(Shipment.ShipmentStatus.IN_TRANSIT);
        }

        if (status == Shipment.ShipmentStatus.DELIVERED) {
            shipment.setActualDeliveryDate(LocalDateTime.now());
        }

        shipmentRepository.save(shipment);
        return ShippingResponse.fromEntity(shipment);
    }

    public ShippingResponse shipOrder(Long orderId) {
        log.info("Shipping order: {}", orderId);

        // Check if shipment already exists
        if (shipmentRepository.findByOrderId(orderId).isPresent()) {
            throw new RuntimeException("Shipment already exists for this order");
        }

        // Create default shipment request
        ShippingRequest request = new ShippingRequest();
        request.setOrderId(orderId);
        request.setRecipientName("Customer");
        request.setRecipientAddress("123 Main St");
        request.setRecipientCity("Anytown");
        request.setRecipientPostalCode("12345");
        request.setRecipientCountry("USA");

        return createShipment(request);
    }
}
