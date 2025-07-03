package com.lin.spring.jackson.service;

import com.lin.spring.jackson.dto.Circle;
import com.lin.spring.jackson.dto.Square;
import com.lin.spring.jackson.dto.Triangle;
import org.springframework.stereotype.Service;

@Service
public class ShapeService {

    public double calculateAreaCircle(Circle circle) {
        double radius = circle.getRadius();
        return ((double) 22 /7) * radius * radius;
    }

    public double calculateAreaTriangle(Triangle triangle) {
        return 0.5 * triangle.getBase() * triangle.getPerpendicularHeight();
    }

    public double calculateAreaSquare(Square square) {
        return square.getLength() * square.getLength();
    }
}
