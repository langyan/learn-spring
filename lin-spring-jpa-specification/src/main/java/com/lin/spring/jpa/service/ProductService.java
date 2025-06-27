package com.lin.spring.jpa.service;


import com.lin.spring.jpa.entity.Product;
import com.lin.spring.jpa.model.SearchProductByCriteria;
import com.lin.spring.jpa.repository.ProductRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jakarta.persistence.criteria.Predicate;

@Service
@RequiredArgsConstructor
public class ProductService {
   private final ProductRepository repository;

    public List<Product> searchByCriteria(SearchProductByCriteria searchProductByCriteria) {
        Specification<Product> specification = (Root<Product> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (Objects.nonNull(searchProductByCriteria.name())) {
                predicates.add(criteriaBuilder.like(root.get("name"), "%" + searchProductByCriteria.name() + "%"));
            }

            if (Objects.nonNull(searchProductByCriteria.category())) {
                predicates.add(criteriaBuilder.like(root.get("category"), "%" + searchProductByCriteria.category() + "%"));
            }

            if (Objects.nonNull(searchProductByCriteria.minPrice())) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("price"), searchProductByCriteria.minPrice()));
            }

            if (Objects.nonNull(searchProductByCriteria.maxPrice())) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("price"), searchProductByCriteria.maxPrice()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return repository.findAll(specification);
    }
}