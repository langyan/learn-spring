package com.lin.spring.orders.inventory.repository;

import com.lin.spring.orders.inventory.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByOrderByProductIdAsc();
}
