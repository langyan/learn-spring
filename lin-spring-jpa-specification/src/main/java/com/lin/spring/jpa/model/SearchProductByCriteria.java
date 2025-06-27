package com.lin.spring.jpa.model;

import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

public record SearchProductByCriteria(
        @RequestParam(required = false) String name,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) BigDecimal minPrice,
        @RequestParam(required = false) BigDecimal maxPrice
) {
}
