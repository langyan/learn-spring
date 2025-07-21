package com.lin.spring.restful.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductHeaderVersioningController {

    @GetMapping(headers = "X-API-VERSION=1")
    public String getProductsV1() {
        return "Header Version 1";
    }

    @GetMapping(headers = "X-API-VERSION=2")
    public String getProductsV2() {
        return "Header Version 2";
    }
}
