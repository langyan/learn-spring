package com.lin.spring.orders.order.pricing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import com.lin.spring.orders.order.exception.PricingUnavailableException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * 服务端定价：调用库存服务的显式 REST API 获取可信价格（超时 + 有界重试，防止雪崩）。
 * 价格在创建订单时快照进 order_items，此后产品调价不影响已有订单。
 */
@Slf4j
@Component
public class PriceCalculator {

    private final RestClient restClient;
    private final int maxAttempts;

    public PriceCalculator(@Value("${app.inventory.base-url}") String baseUrl,
                           @Value("${app.inventory.connect-timeout-ms}") int connectTimeoutMs,
                           @Value("${app.inventory.read-timeout-ms}") int readTimeoutMs,
                           @Value("${app.inventory.max-attempts}") int maxAttempts) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.maxAttempts = maxAttempts;
    }

    public PricedLine price(long productId) {
        RestClientResponseException lastHttpError = null;
        ResourceAccessException lastIoError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ProductView product = restClient.get()
                        .uri("/api/v1/products/{id}", productId)
                        .retrieve()
                        .body(ProductView.class);
                if (product == null) {
                    throw new PricingUnavailableException("Product " + productId + " returned empty");
                }
                return new PricedLine(product.productId(), product.name(), product.price(), product.currency());
            } catch (RestClientResponseException e) {
                lastHttpError = e;
                if (e.getStatusCode().is4xxClientError()) {
                    // 4xx 重试无意义：商品不存在等
                    throw new PricingUnavailableException("Product " + productId + " not available: " + e.getStatusCode());
                }
                log.warn("Pricing attempt {}/{} for product {} failed: {}", attempt, maxAttempts, productId, e.getMessage());
            } catch (ResourceAccessException e) {
                lastIoError = e;
                log.warn("Pricing attempt {}/{} for product {} failed: {}", attempt, maxAttempts, productId, e.getMessage());
            }
        }
        String reason = lastHttpError != null ? lastHttpError.getMessage()
                : lastIoError != null ? lastIoError.getMessage() : "unknown";
        throw new PricingUnavailableException("Pricing unavailable for product " + productId + ": " + reason);
    }

    /** 对全部商品行定价，并校验币种一致。 */
    public PricedOrder priceAll(List<com.lin.spring.orders.order.dto.CreateOrderRequest.Item> items) {
        var lines = items.stream()
                .map(i -> new PricedQuantity(price(i.productId()), i.quantity()))
                .toList();
        String currency = lines.get(0).line().currency();
        for (PricedQuantity l : lines) {
            if (!currency.equals(l.line().currency())) {
                throw new PricingUnavailableException("Mixed currencies across products not supported");
            }
        }
        return new PricedOrder(lines, currency);
    }

    public record ProductView(Long productId, String name, BigDecimal price, String currency) {
    }

    public record PricedLine(long productId, String name, BigDecimal unitPrice, String currency) {
    }

    public record PricedQuantity(PricedLine line, int quantity) {
    }

    public record PricedOrder(List<PricedQuantity> lines, String currency) {
    }
}
