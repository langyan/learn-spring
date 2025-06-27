package com.lin.spring.jpa.controller;

import com.lin.spring.jpa.entity.Product;
import com.lin.spring.jpa.model.SearchProductByCriteria;
import com.lin.spring.jpa.service.ProductService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
//@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductController {

   private final ProductService productService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Product>> getAllByFilters(SearchProductByCriteria searchProductByCriteria) {
        return ResponseEntity.ok(productService.searchByCriteria(searchProductByCriteria));
    }
}
