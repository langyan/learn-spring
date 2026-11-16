package com.lin.spring.moviebooking.booking.client;

import com.lin.spring.moviebooking.booking.config.BookingProperties;
import com.lin.spring.moviebooking.common.dto.PaymentCommand;
import com.lin.spring.moviebooking.common.dto.PaymentInitiationResponse;
import com.lin.spring.moviebooking.common.dto.PaymentStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class HttpPaymentGatewayClient implements PaymentGatewayClient {

    private final RestClient restClient;

    public HttpPaymentGatewayClient(@LoadBalanced RestClient.Builder builder,
                                    BookingProperties properties) {
        this.restClient = builder
                .baseUrl("http://" + properties.getPayment().getServiceId())
                .build();
    }

    @Override
    public PaymentInitiationResponse createIntent(PaymentCommand command) {
        log.info("Creating payment intent for booking {} (channel={}, scenario={})",
                command.bookingReference(), command.channel(), command.scenario());
        return restClient.post()
                .uri("/api/v1/payments/intents")
                .body(command)
                .retrieve()
                .body(PaymentInitiationResponse.class);
    }

    @Override
    public PaymentStatusResponse queryStatus(String gatewayRef) {
        return restClient.get()
                .uri("/api/v1/payments/{gatewayRef}/status", gatewayRef)
                .retrieve()
                .body(PaymentStatusResponse.class);
    }
}
