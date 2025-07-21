package com.lin.spring.restful.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductMediaTypeVersioningController {

    @GetMapping(produces = "application/vnd.myapp.v1+json")
    public String getProductsV1() {
        return "Media Type Version 1";
    }

    @GetMapping(produces = "application/vnd.myapp.v2+json")
    public String getProductsV2() {
        return "Media Type Version 2";
    }
}
