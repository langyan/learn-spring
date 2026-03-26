package com.lin.spring.elasticsearch.repository;

import com.lin.spring.elasticsearch.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductJpaRepository extends JpaRepository<Product, Long> {
    List<Product> findByCategory(String category);
}
