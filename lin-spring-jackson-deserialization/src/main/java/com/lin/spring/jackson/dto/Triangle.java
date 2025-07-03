package com.lin.spring.jackson.dto;

import lombok.Data;

@Data
public class Triangle implements Shape {
    double base;
    double perpendicularHeight;
}