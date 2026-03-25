package com.lin.spring.service.shipping.dto;

import com.lin.spring.service.shipping.model.Shipment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShippingResponse {

    private Long id;
    private String trackingNumber;
    private Long orderId;
    private String recipientName;
    private String recipientAddress;
    private String recipientCity;
    private String recipientPostalCode;
    private String recipientCountry;
    private Shipment.ShipmentStatus status;
    private String carrier;
    private LocalDateTime estimatedDeliveryDate;
    private LocalDateTime actualDeliveryDate;
    private LocalDateTime createdAt;

    public static ShippingResponse fromEntity(Shipment shipment) {
        return new ShippingResponse(
                shipment.getId(),
                shipment.getTrackingNumber(),
                shipment.getOrderId(),
                shipment.getRecipientName(),
                shipment.getRecipientAddress(),
                shipment.getRecipientCity(),
                shipment.getRecipientPostalCode(),
                shipment.getRecipientCountry(),
                shipment.getStatus(),
                shipment.getCarrier(),
                shipment.getEstimatedDeliveryDate(),
                shipment.getActualDeliveryDate(),
                shipment.getCreatedAt()
        );
    }
}
