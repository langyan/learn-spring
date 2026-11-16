package com.lin.spring.moviebooking.payment.client;

import com.lin.spring.moviebooking.common.dto.WebhookPayload;
import com.lin.spring.moviebooking.payment.config.PaymentGatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Delivers the simulated gateway's webhook to booking-service, with bounded
 * retries and exponential backoff — exactly how a real PSP behaves.
 */
@Slf4j
@Component
public class WebhookDeliveryClient {

    private final RestClient restClient;
    private final PaymentGatewayProperties properties;

    public WebhookDeliveryClient(@LoadBalanced RestClient.Builder builder,
                                 PaymentGatewayProperties properties) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl("http://" + properties.getBookingServiceId())
                .build();
    }

    /** @return true when booking-service accepted (2xx). */
    public boolean deliver(WebhookPayload payload) {
        for (int attempt = 1; attempt <= properties.getWebhookMaxRetries(); attempt++) {
            try {
                restClient.post()
                        .uri("/api/v1/webhooks/payments")
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();
                return true;
            } catch (Exception ex) {
                long backoff = 1000L << (attempt - 1);   // 1s, 2s, 4s...
                log.warn("Webhook delivery attempt {}/{} failed for {} ({}), backing off {}ms",
                        attempt, properties.getWebhookMaxRetries(),
                        payload.gatewayRef(), ex.getMessage(), backoff);
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        log.error("Webhook delivery abandoned for gatewayRef={} — booking will resolve via status query",
                payload.gatewayRef());
        return false;
    }
}
