package com.lin.spring.elasticsearch.service;

import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.json.JsonData;
import com.lin.spring.elasticsearch.dto.PriceRangeStats;
import com.lin.spring.elasticsearch.dto.SearchCriteria;
import com.lin.spring.elasticsearch.dto.SearchResultWithAggregations;
import com.lin.spring.elasticsearch.entity.ProductDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for product search operations using Elasticsearch
 * Uses IK analyzer for Chinese text search
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    private static final String PRODUCT_INDEX = "products";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Simple search by keyword
     */
    public List<ProductDocument> search(String keyword, int page, int size) {
        log.info("Searching products with keyword: {}, page: {}, size: {}", keyword, page, size);

        int validatedSize = Math.min(size, MAX_PAGE_SIZE);
        if (validatedSize <= 0) validatedSize = DEFAULT_PAGE_SIZE;

        NativeQueryBuilder queryBuilder = NativeQuery.builder();

        if (keyword != null && !keyword.isBlank()) {
            queryBuilder.withQuery(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q
                    .multiMatch(m -> m
                            .query(keyword)
                            .fields("name^2", "description", "tags", "category")
                            .type(TextQueryType.BestFields)
                            .fuzziness("AUTO")
                    )
            ));
        } else {
            queryBuilder.withQuery(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.matchAll(m -> m)));
        }

        queryBuilder.withPageable(PageRequest.of(page, validatedSize));

        SearchHits<ProductDocument> searchHits =
            elasticsearchOperations.search(queryBuilder.build(), ProductDocument.class, IndexCoordinates.of(PRODUCT_INDEX));

        List<ProductDocument> results = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        log.info("Found {} products", results.size());
        return results;
    }

    /**
     * Advanced search with multiple criteria
     */
    public SearchResultWithAggregations<ProductDocument> advancedSearch(SearchCriteria criteria) {
        log.info("Advanced search with criteria: {}", criteria);

        int page = criteria.getPage() != null ? criteria.getPage() : 0;
        int size = criteria.getSize() != null ? Math.min(criteria.getSize(), MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        NativeQueryBuilder queryBuilder = NativeQuery.builder();
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

        if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
            boolQueryBuilder.must(m -> m
                    .multiMatch(mm -> mm
                            .query(criteria.getKeyword())
                            .fields("name^3", "description^2", "tags", "category")
                            .type(TextQueryType.BestFields)
                            .analyzer("ik_max_word")
                    )
            );
        }

        if (criteria.getCategory() != null && !criteria.getCategory().isBlank()) {
            boolQueryBuilder.filter(f -> f.term(t -> t.field("category").value(criteria.getCategory())));
        }

        if (criteria.getMinPrice() != null || criteria.getMaxPrice() != null) {
            // TODO: Implement price range filter when Elasticsearch client is properly configured
            log.warn("Price range filtering not yet implemented: min={}, max={}",
                    criteria.getMinPrice(), criteria.getMaxPrice());
        }

        if (criteria.getTag() != null && !criteria.getTag().isBlank()) {
            boolQueryBuilder.filter(f -> f.term(t -> t.field("tags").value(criteria.getTag())));
        }

        queryBuilder.withQuery(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.bool(boolQueryBuilder.build())));

        if (criteria.getSortField() != null && !criteria.getSortField().isBlank()) {
            Sort.Direction direction = "desc".equalsIgnoreCase(criteria.getSortDirection())
                    ? Sort.Direction.DESC : Sort.Direction.ASC;
            queryBuilder.withSort(Sort.by(direction, criteria.getSortField()));
        } else {
            queryBuilder.withSort(Sort.by(Sort.Direction.DESC, "_score"));
        }

        queryBuilder.withPageable(PageRequest.of(page, size));

        SearchHits<ProductDocument> searchHits =
            elasticsearchOperations.search(queryBuilder.build(), ProductDocument.class, IndexCoordinates.of(PRODUCT_INDEX));

        List<ProductDocument> results = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        long totalHits = searchHits.getTotalHits();
        long totalPages = (totalHits + size - 1) / size;

        log.info("Advanced search found {} results (total: {})", results.size(), totalHits);

        return SearchResultWithAggregations.<ProductDocument>builder()
                .results(results)
                .totalHits(totalHits)
                .page(page)
                .pageSize(size)
                .totalPages(totalPages)
                .aggregations(new HashMap<>())
                .priceRanges(new ArrayList<>())
                .build();
    }

    /**
     * Aggregate products by category
     */
    public Map<String, Long> aggregateByCategory() {
        log.info("Aggregating products by category");

        SearchHits<ProductDocument> searchHits =
            elasticsearchOperations.search(NativeQuery.builder().build(), ProductDocument.class, IndexCoordinates.of(PRODUCT_INDEX));

        Map<String, Long> categoryCounts = new HashMap<>();

        searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .forEach(product -> {
                    String category = product.getCategory();
                    if (category != null) {
                        categoryCounts.merge(category, 1L, Long::sum);
                    }
                });

        log.info("Category aggregation: {}", categoryCounts);
        return categoryCounts;
    }

    /**
     * Aggregate products by price ranges
     */
    public List<PriceRangeStats> aggregateByPriceRanges() {
        log.info("Aggregating products by price ranges");

        SearchHits<ProductDocument> searchHits =
            elasticsearchOperations.search(NativeQuery.builder().build(), ProductDocument.class, IndexCoordinates.of(PRODUCT_INDEX));

        List<PriceRangeStats> priceRanges = new ArrayList<>();
        Map<String, Long> rangeCounts = new HashMap<>();

        searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .forEach(product -> {
                    Double price = product.getPrice();
                    if (price != null) {
                        String range;
                        if (price < 100) {
                            range = "0-100";
                        } else if (price < 500) {
                            range = "100-500";
                        } else if (price < 1000) {
                            range = "500-1000";
                        } else {
                            range = "1000+";
                        }
                        rangeCounts.merge(range, 1L, Long::sum);
                    }
                });

        rangeCounts.forEach((r, count) -> {
            priceRanges.add(PriceRangeStats.builder()
                    .range(r)
                    .count(count)
                    .products(new ArrayList<>())
                    .build());
        });

        log.info("Price range aggregation: {} ranges", priceRanges.size());
        return priceRanges;
    }

    /**
     * Search with aggregations included
     */
    public SearchResultWithAggregations<ProductDocument> searchWithAggregations(
            String keyword,
            String category,
            int page,
            int size
    ) {
        log.info("Search with aggregations - keyword: {}, category: {}", keyword, category);

        int validatedSize = Math.min(size, MAX_PAGE_SIZE);
        if (validatedSize <= 0) validatedSize = DEFAULT_PAGE_SIZE;

        NativeQueryBuilder queryBuilder = NativeQuery.builder();
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

        if (keyword != null && !keyword.isBlank()) {
            boolQueryBuilder.must(m -> m
                    .multiMatch(mm -> mm
                            .query(keyword)
                            .fields("name^2", "description")
                            .type(TextQueryType.BestFields)
                            .analyzer("ik_max_word")
                    )
            );
        } else {
            boolQueryBuilder.must(m -> m.matchAll(ma -> ma));
        }

        if (category != null && !category.isBlank()) {
            boolQueryBuilder.filter(f -> f.term(t -> t.field("category").value(category)));
        }

        queryBuilder.withQuery(co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.bool(boolQueryBuilder.build())));
        queryBuilder.withPageable(PageRequest.of(page, validatedSize));

        SearchHits<ProductDocument> searchHits =
            elasticsearchOperations.search(queryBuilder.build(), ProductDocument.class, IndexCoordinates.of(PRODUCT_INDEX));

        List<ProductDocument> results = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        long totalHits = searchHits.getTotalHits();
        long totalPages = (totalHits + size - 1) / size;

        Map<String, Long> categoryAggs = new HashMap<>();
        List<PriceRangeStats> priceRanges = new ArrayList<>();

        results.forEach(product -> {
            String cat = product.getCategory();
            if (cat != null) {
                categoryAggs.merge(cat, 1L, Long::sum);
            }

            Double price = product.getPrice();
            if (price != null) {
                String range;
                if (price < 100) {
                    range = "0-100";
                } else if (price < 500) {
                    range = "100-500";
                } else if (price < 1000) {
                    range = "500-1000";
                } else {
                    range = "1000+";
                }

                final String finalRange = range;
                boolean found = false;
                for (PriceRangeStats pr : priceRanges) {
                    if (pr.getRange().equals(finalRange)) {
                        pr.setCount(pr.getCount() + 1);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    priceRanges.add(PriceRangeStats.builder()
                            .range(range)
                            .count(1L)
                            .products(new ArrayList<>())
                            .build());
                }
            }
        });

        Map<String, Map<String, Long>> aggregationsMap = new HashMap<>();
        aggregationsMap.put("categories", categoryAggs);

        log.info("Search with aggregations: {} results, {} categories, {} price ranges",
                results.size(), categoryAggs.size(), priceRanges.size());

        return SearchResultWithAggregations.<ProductDocument>builder()
                .results(results)
                .totalHits(totalHits)
                .page(page)
                .pageSize(size)
                .totalPages(totalPages)
                .aggregations(aggregationsMap)
                .priceRanges(priceRanges)
                .build();
    }
}
