package com.lin.spring.elasticsearch.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product data DTO for Kafka events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductData {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private String category;
    private String brand;
    private List<String> tags;
    private String sku;
    private Boolean active;
    private String manufacturer;
    private String countryOfOrigin;
}
