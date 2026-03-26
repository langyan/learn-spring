package com.lin.spring.elasticsearch.controller;

import com.lin.spring.elasticsearch.dto.*;
import com.lin.spring.elasticsearch.entity.Product;
import com.lin.spring.elasticsearch.entity.ProductDocument;
import com.lin.spring.elasticsearch.service.ProductReadService;
import com.lin.spring.elasticsearch.service.ProductSearchService;
import com.lin.spring.elasticsearch.service.ProductWriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductWriteService writeService;
    private final ProductReadService readService;
    private final ProductSearchService searchService;

    // Write Operations
    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody ProductCreateRequest request) {
        Product created = writeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request) {
        try {
            Product updated = writeService.update(id, request);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            writeService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Read Operations
    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(readService.getById(id));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<Product>> getAll() {
        return ResponseEntity.ok(readService.getAll());
    }

    // Search Operations
    @GetMapping("/search")
    public ResponseEntity<List<ProductDocument>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(searchService.search(keyword, page, size));
    }

    @PostMapping("/advanced")
    public ResponseEntity<SearchResultWithAggregations<ProductDocument>> advancedSearch(
            @Valid @RequestBody SearchCriteria criteria,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        criteria.setPage(page);
        criteria.setSize(size);
        return ResponseEntity.ok(searchService.advancedSearch(criteria));
    }

    @GetMapping("/aggregate/category")
    public ResponseEntity<List<CategoryStats>> aggregateByCategory(
            @RequestParam(required = false) String category) {
        // For now, return empty list - aggregation can be enhanced later
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/aggregate/price")
    public ResponseEntity<PriceRangeStats> aggregateByPriceRange(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice) {
        // For now, return empty stats - aggregation can be enhanced later
        return ResponseEntity.ok(PriceRangeStats.builder().build());
    }

    @GetMapping("/search-with-aggregations")
    public ResponseEntity<SearchResultWithAggregations<ProductDocument>> searchWithAggregations(
            @RequestParam String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(searchService.searchWithAggregations(keyword, category, page, size));
    }
}
