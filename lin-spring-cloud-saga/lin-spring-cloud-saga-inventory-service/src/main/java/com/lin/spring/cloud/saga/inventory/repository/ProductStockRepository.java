package com.lin.spring.cloud.saga.inventory.repository;

import com.lin.spring.cloud.saga.inventory.entity.ProductStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {
    Optional<ProductStock> findByProductId(String productId);
    boolean existsByProductId(String productId);
}