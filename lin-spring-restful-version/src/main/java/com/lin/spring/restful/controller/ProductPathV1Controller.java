package com.lin.spring.restful.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author gy.lin
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductPathV1Controller {
    @GetMapping
    public String getProductsV1() {
        return "Products from API version 1";
    }
}
