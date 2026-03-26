package com.lin.spring.elasticsearch.repository;

import com.lin.spring.elasticsearch.entity.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductDocumentRepository extends ElasticsearchRepository<ProductDocument, String> {

    List<ProductDocument> findByName(String name);
    Page<ProductDocument> findByCategory(String category, Pageable pageable);
    Page<ProductDocument> findByPriceBetween(Double min, Double max, Pageable pageable);

    Page<ProductDocument> findByNameContainingOrDescriptionContaining(
            String name, String description, Pageable pageable
    );

    Page<ProductDocument> findByCategoryAndPriceLessThanEqual(
            String category, Double maxPrice, Pageable pageable
    );
}
