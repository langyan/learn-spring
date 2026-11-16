package com.lin.spring.orders.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 注意：客户端不传价格——价格由服务端查询并快照（文章第 13 节：绝不信任客户端价格）。
 */
public record CreateOrderRequest(
        @NotEmpty @Size(max = 20) List<@Valid Item> items) {

    public record Item(
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long productId,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(1)
            @jakarta.validation.constraints.Max(100) Integer quantity) {
    }
}
