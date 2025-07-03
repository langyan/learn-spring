package com.lin.spring.jackson.controller;

import com.lin.spring.jackson.dto.Circle;
import com.lin.spring.jackson.dto.Shape;
import com.lin.spring.jackson.dto.Square;
import com.lin.spring.jackson.dto.Triangle;
import com.lin.spring.jackson.service.ShapeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class ShapeController {

    private final ShapeService shapeService;

    public ShapeController(ShapeService shapeService) {
        this.shapeService = shapeService;
    }

    @PostMapping("/area/calculate")
    public ResponseEntity<Double> calculateArea(@RequestBody Shape shape) {
        double area = 0;
        switch (shape) {
            case Circle circle -> area = shapeService.calculateAreaCircle(circle);
            case Triangle triangle -> area = shapeService.calculateAreaTriangle(triangle);
            case Square square -> area = shapeService.calculateAreaSquare(square);
            default -> log.warn("Unknown shape: {}", shape);

        }
        return ResponseEntity.ok(area);
    }
}