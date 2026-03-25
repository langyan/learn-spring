package com.lin.spring.service.inventory.dto;

import com.lin.spring.service.inventory.model.Inventory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {

    private Long id;
    private String productCode;
    private String productName;
    private Integer quantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private Boolean inStock;
    private Boolean lowStock;
    private Double price;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static InventoryResponse fromEntity(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getProductCode(),
                inventory.getProductName(),
                inventory.getQuantity(),
                inventory.getReservedQuantity(),
                inventory.getAvailableQuantity(),
                inventory.isInStock(),
                inventory.isLowStock(),
                inventory.getPrice(),
                inventory.getCreatedAt(),
                inventory.getUpdatedAt()
        );
    }
}
