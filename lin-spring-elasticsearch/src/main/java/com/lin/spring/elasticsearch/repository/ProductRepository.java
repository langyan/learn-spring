package com.lin.spring.elasticsearch.repository;

import com.lin.spring.elasticsearch.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends ElasticsearchRepository<Product, String> {

    List<Product> findByName(String name);
    Page<Product> findByCategory(String category, Pageable pageable);
    Page<Product> findByPriceBetween(Double min, Double max, Pageable pageable);

    Page<Product> findByNameContainingOrDescriptionContaining(
            String name, String description, Pageable pageable
    );

    Page<Product> findByCategoryAndPriceLessThanEqual(
            String category, Double maxPrice, Pageable pageable
    );
}
