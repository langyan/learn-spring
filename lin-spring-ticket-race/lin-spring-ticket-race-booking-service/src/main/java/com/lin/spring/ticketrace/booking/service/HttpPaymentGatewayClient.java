package com.lin.spring.ticketrace.booking.service;

import com.lin.spring.ticketrace.common.dto.PaymentCommand;
import com.lin.spring.ticketrace.common.dto.PaymentResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpPaymentGatewayClient implements PaymentGatewayClient {

    private final RestClient paymentRestClient;

    public HttpPaymentGatewayClient(
            RestClient.Builder restClientBuilder,
            @Value("${ticket-race.payment.service-id}") String paymentServiceId
    ) {
        this.paymentRestClient = restClientBuilder.baseUrl("http://" + paymentServiceId).build();
    }

    @Override
    public PaymentResult process(PaymentCommand command) {
        return paymentRestClient.post()
                .uri("/api/payments/simulate")
                .body(command)
                .retrieve()
                .body(PaymentResult.class);
    }
}
