package com.lin.spring.elasticsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Search result with aggregations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultWithAggregations<T> {

    private List<T> results;

    private Long totalHits;

    private Integer page;

    private Integer pageSize;

    private Long totalPages;

    private Map<String, Map<String, Long>> aggregations;

    private List<PriceRangeStats> priceRanges;
}
