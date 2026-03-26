package com.lin.spring.elasticsearch.repository;

import com.lin.spring.elasticsearch.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByName(String name);

    @Query("SELECT p FROM Product p WHERE " +
           "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Product> searchByNameOrDescription(@Param("keyword") String keyword);

    @Query("SELECT p FROM Product p WHERE p.category = :category")
    List<Product> findByCategory(@Param("category") String category);

    @Query("SELECT p FROM Product p WHERE p.price BETWEEN :min AND :max")
    List<Product> findByPriceBetween(@Param("min") Double min, @Param("max") Double max);

    @Query("SELECT p FROM Product p WHERE p.category = :category AND p.price <= :maxPrice")
    List<Product> findByCategoryAndPriceLessThanEqual(
            @Param("category") String category,
            @Param("maxPrice") Double maxPrice
    );
}
