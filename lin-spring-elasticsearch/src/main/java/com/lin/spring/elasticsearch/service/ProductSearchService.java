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
import org.springframework.data.elasticsearch.core.query.Query;
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

        // Validate page size
        int validatedSize = Math.min(size, MAX_PAGE_SIZE);
        if (validatedSize <= 0) validatedSize = DEFAULT_PAGE_SIZE;

        // Build query with IK analyzer
        NativeQueryBuilder queryBuilder = NativeQuery.builder();

        if (keyword != null && !keyword.isBlank()) {
            Query query = Query.of(q -> q
                    .multiMatch(m -> m
                            .query(keyword)
                            .fields("name^2", "description", "tags", "category")
                            .type(TextQueryType.BestFields)
                            .fuzziness("AUTO")
                    )
            );
            queryBuilder.withQuery(query);
        } else {
            queryBuilder.withQuery(Query.of(q -> q.matchAll(m -> m)));
        }

        // Add pagination
        PageRequest pageRequest = PageRequest.of(page, validatedSize);
        queryBuilder.withPageable(pageRequest);

        Query query = queryBuilder.build();

        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(query, ProductDocument.class);

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

        // Validate pagination parameters
        int page = criteria.getPage() != null ? criteria.getPage() : 0;
        int size = criteria.getSize() != null ? Math.min(criteria.getSize(), MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        NativeQueryBuilder queryBuilder = NativeQuery.builder();

        // Build bool query for multiple criteria
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

        // Keyword search with IK analyzer
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

        // Category filter
        if (criteria.getCategory() != null && !criteria.getCategory().isBlank()) {
            boolQueryBuilder.filter(f -> f
                    .term(t -> t
                            .field("category")
                            .value(criteria.getCategory())
                    )
            );
        }

        // Price range filter
        if (criteria.getMinPrice() != null || criteria.getMaxPrice() != null) {
            var rangeQuery = new RangeQuery.Builder();
            var fieldQuery = rangeQuery.field("price");

            if (criteria.getMinPrice() != null) {
                fieldQuery.gte(JsonData.of(criteria.getMinPrice()));
            }
            if (criteria.getMaxPrice() != null) {
                fieldQuery.lte(JsonData.of(criteria.getMaxPrice()));
            }

            boolQueryBuilder.filter(f -> f.range(fieldQuery.build()));
        }

        // Tag filter
        if (criteria.getTag() != null && !criteria.getTag().isBlank()) {
            boolQueryBuilder.filter(f -> f
                    .term(t -> t
                            .field("tags")
                            .value(criteria.getTag())
                    )
            );
        }

        queryBuilder.withQuery(Query.of(q -> q.bool(boolQueryBuilder.build())));

        // Add sorting
        if (criteria.getSortField() != null && !criteria.getSortField().isBlank()) {
            Sort.Direction direction = "desc".equalsIgnoreCase(criteria.getSortDirection())
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            queryBuilder.withSort(Sort.by(direction, criteria.getSortField()));
        } else {
            // Default sort by relevance
            queryBuilder.withSort(Sort.by(Sort.Direction.DESC, "_score"));
        }

        // Add pagination
        PageRequest pageRequest = PageRequest.of(page, size);
        queryBuilder.withPageable(pageRequest);

        Query query = queryBuilder.build();

        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(query, ProductDocument.class);

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

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.matchAll(m -> m))
                .withAggregation("categories", a -> a
                        .terms(t -> t
                                .field("category")
                                .size(100)
                        )
                )
                .withMaxResults(0) // We only need aggregations
                .build();

        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(query, ProductDocument.class);

        Map<String, Long> categoryCounts = new HashMap<>();

        searchHits.getAggregations().get("categories")
                .getAggregate()
                .sterms()
                .buckets().array()
                .forEach(bucket -> {
                    categoryCounts.put(bucket.key(), bucket.docCount());
                });

        log.info("Category aggregation: {}", categoryCounts);
        return categoryCounts;
    }

    /**
     * Aggregate products by price ranges
     */
    public List<PriceRangeStats> aggregateByPriceRanges() {
        log.info("Aggregating products by price ranges");

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.matchAll(m -> m))
                .withAggregation("price_ranges", a -> a
                        .range(r -> r
                                .field("price")
                                .ranges(range -> range.to("100.0").key("0-100"))
                                .ranges(range -> range.from("100.0").to("500.0").key("100-500"))
                                .ranges(range -> range.from("500.0").to("1000.0").key("500-1000"))
                                .ranges(range -> range.from("1000.0").key("1000+"))
                        )
                )
                .withMaxResults(0)
                .build();

        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(query, ProductDocument.class);

        List<PriceRangeStats> priceRanges = new ArrayList<>();

        searchHits.getAggregations().get("price_ranges")
                .getAggregate()
                .range()
                .buckets().array()
                .forEach(bucket -> {
                    priceRanges.add(PriceRangeStats.builder()
                            .range(bucket.key())
                            .count(bucket.docCount())
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

        // Build query
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
            boolQueryBuilder.filter(f -> f
                    .term(t -> t
                            .field("category")
                            .value(category)
                    )
            );
        }

        queryBuilder.withQuery(Query.of(q -> q.bool(boolQueryBuilder.build())));

        // Add aggregations
        queryBuilder.withAggregation("categories", a -> a
                .terms(t -> t
                        .field("category")
                        .size(50)
                )
        );

        queryBuilder.withAggregation("price_ranges", a -> a
                .range(r -> r
                        .field("price")
                        .ranges(range -> range.to("100.0").key("0-100"))
                        .ranges(range -> range.from("100.0").to("500.0").key("100-500"))
                        .ranges(range -> range.from("500.0").to("1000.0").key("500-1000"))
                        .ranges(range -> range.from("1000.0").key("1000+"))
                )
        );

        // Add pagination
        PageRequest pageRequest = PageRequest.of(page, validatedSize);
        queryBuilder.withPageable(pageRequest);

        Query query = queryBuilder.build();

        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(query, ProductDocument.class);

        // Extract results
        List<ProductDocument> results = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        long totalHits = searchHits.getTotalHits();
        long totalPages = (totalHits + size - 1) / size;

        // Extract category aggregations
        Map<String, Long> categoryAggs = new HashMap<>();
        searchHits.getAggregations().get("categories")
                .getAggregate()
                .sterms()
                .buckets().array()
                .forEach(bucket -> categoryAggs.put(bucket.key(), bucket.docCount()));

        // Extract price range aggregations
        List<PriceRangeStats> priceRanges = new ArrayList<>();
        searchHits.getAggregations().get("price_ranges")
                .getAggregate()
                .range()
                .buckets().array()
                .forEach(bucket -> {
                    priceRanges.add(PriceRangeStats.builder()
                            .range(bucket.key())
                            .count(bucket.docCount())
                            .products(new ArrayList<>())
                            .build());
                });

        Map<String, Map<String, Long>> aggregations = new HashMap<>();
        aggregations.put("categories", categoryAggs);

        log.info("Search with aggregations: {} results, {} categories, {} price ranges",
                results.size(), categoryAggs.size(), priceRanges.size());

        return SearchResultWithAggregations.<ProductDocument>builder()
                .results(results)
                .totalHits(totalHits)
                .page(page)
                .pageSize(size)
                .totalPages(totalPages)
                .aggregations(aggregations)
                .priceRanges(priceRanges)
                .build();
    }
}
