package com.lin.spring.service.order.service.client;

import com.lin.spring.service.order.dto.ShippingResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "shipping-service", path = "/api/shipping")
public interface ShippingServiceClient {

    @PostMapping("/order/{orderId}/ship")
    ShippingResponse shipOrder(@PathVariable Long orderId);

    @GetMapping("/order/{orderId}")
    ShippingResponse getShipmentByOrderId(@PathVariable Long orderId);
}
