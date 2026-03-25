package com.lin.spring.service.shipping.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "shipments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String trackingNumber;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private String recipientName;

    @Column(nullable = false)
    private String recipientAddress;

    @Column(nullable = false)
    private String recipientCity;

    @Column(nullable = false)
    private String recipientPostalCode;

    @Column(nullable = false)
    private String recipientCountry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status;

    private String carrier;

    private LocalDateTime estimatedDeliveryDate;

    private LocalDateTime actualDeliveryDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (trackingNumber == null) {
            trackingNumber = generateTrackingNumber();
        }
        if (status == null) {
            status = ShipmentStatus.PENDING;
        }
    }

    private String generateTrackingNumber() {
        return "TRK-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
    }

    public enum ShipmentStatus {
        PENDING,
        PROCESSING,
        SHIPPED,
        IN_TRANSIT,
        DELIVERED,
        CANCELLED,
        RETURNED
    }
}
