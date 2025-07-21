package com.lin.spring.restful.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductParamVersioningController {

    @GetMapping(params = "version=1")
    public String getProductsV1() {
        return "Version 1 - Products";
    }

    @GetMapping(params = "version=2")
    public String getProductsV2() {
        return "Version 2 - Products";
    }
}
