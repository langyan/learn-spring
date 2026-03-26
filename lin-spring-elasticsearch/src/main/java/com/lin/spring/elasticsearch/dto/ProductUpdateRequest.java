package com.lin.spring.elasticsearch.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request DTO for updating a product
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpdateRequest {

    @Size(min = 2, max = 200, message = "Product name must be between 2 and 200 characters")
    private String name;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    @DecimalMin(value = "0.0", inclusive = true, message = "Price must be positive")
    private BigDecimal price;

    @Min(value = 0, message = "Stock must be non-negative")
    private Integer stock;

    private String category;

    @Size(max = 100, message = "Brand must not exceed 100 characters")
    private String brand;

    private List<String> tags;

    @Size(min = 2, max = 50, message = "SKU must be between 2 and 50 characters")
    private String sku;

    private Boolean active;

    @Size(max = 100, message = "Manufacturer must not exceed 100 characters")
    private String manufacturer;

    @Size(max = 100, message = "Country of origin must not exceed 100 characters")
    private String countryOfOrigin;
}
