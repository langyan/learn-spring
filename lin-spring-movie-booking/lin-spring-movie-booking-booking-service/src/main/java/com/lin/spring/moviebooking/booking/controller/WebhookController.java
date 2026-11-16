package com.lin.spring.moviebooking.booking.controller;

import com.lin.spring.moviebooking.booking.service.WebhookService;
import com.lin.spring.moviebooking.common.dto.WebhookPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment webhook ingress. Always answer 2xx once accepted: the payment
 * gateway stops retrying on 2xx, duplicates collapse in WebhookService.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping("/payments")
    public ResponseEntity<String> payments(@RequestBody WebhookPayload payload) {
        log.info("Webhook received: gatewayRef={} result={}",
                payload.gatewayRef(), payload.result());
        webhookService.handle(payload);
        return ResponseEntity.ok("accepted");
    }
}
