package com.lin.spring.service.order.service.client;

import com.lin.spring.service.order.dto.InventoryReservationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "inventory-service", path = "/api/inventory")
public interface InventoryServiceClient {

    @PostMapping("/reserve")
    Boolean reserveStock(@RequestBody InventoryReservationRequest request);

    @PostMapping("/confirm")
    void confirmReservation(@RequestParam String productCode, @RequestParam Integer quantity);

    @PostMapping("/release")
    void releaseReservation(@RequestParam String productCode, @RequestParam Integer quantity);
}
