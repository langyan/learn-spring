package com.lin.spring.jpa.repository;

import java.util.List;

import com.lin.spring.jpa.model.Product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.QueryHint;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p WHERE p.category = :category AND p.available = true")
    @QueryHints({
        @QueryHint(name = "org.hibernate.readOnly", value = "true"),
        @QueryHint(name = "org.hibernate.fetchSize", value = "50"),
        @QueryHint(name = "org.hibernate.cacheable", value = "true"),
        @QueryHint(name = "jakarta.persistence.cache.retrieveMode", value = "USE"),
        @QueryHint(name = "jakarta.persistence.cache.storeMode", value = "USE"),
        @QueryHint(name = "jakarta.persistence.query.timeout", value = "2000")
    })
    List<Product> findOptimizedByCategory(@Param("category") String category);
}