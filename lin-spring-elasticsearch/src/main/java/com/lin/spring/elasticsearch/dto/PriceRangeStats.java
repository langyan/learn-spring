package com.lin.spring.elasticsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Price range statistics for aggregations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceRangeStats {

    private String range;
    private Long count;
    private List<ProductSummary> products;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSummary {
        private Long id;
        private String name;
        private Double price;
        private String category;
    }
}
