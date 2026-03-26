package com.lin.spring.elasticsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Search criteria DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchCriteria {

    private String keyword;

    private String category;

    private String brand;

    private String tag;

    private String manufacturer;

    private Boolean active;

    private Double minPrice;

    private Double maxPrice;

    private Integer minStock;

    private Integer page;

    private Integer size;

    private String sortField;

    private String sortDirection;
}
