package com.lin.spring.restful.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author gy.lin
 */
@RestController
@RequestMapping("/api/v2/products")
public class ProductPathV2Controller {
    @GetMapping
    public String getProductsV2() {
        return "Products from API version 2";
    }
}
