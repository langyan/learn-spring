package com.lin.spring.orders.inventory.controller;

import com.lin.spring.orders.inventory.domain.Product;
import com.lin.spring.orders.inventory.service.ProductCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 产品目录 API（公开只读 + 演示用管理端点）。
 * GET /api/v1/products/{id} 也供 order-service 服务端定价调用（显式 API，而非直连他人数据库）。
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductCatalogService catalogService;

    public record ProductView(Long productId, String name, String description,
                              BigDecimal price, String currency) {
        static ProductView of(Product p) {
            return new ProductView(p.getProductId(), p.getName(), p.getDescription(),
                    p.getUnitPrice(), p.getCurrency());
        }
    }

    @GetMapping
    public List<ProductView> all() {
        return catalogService.all().stream().map(ProductView::of).toList();
    }

    @GetMapping("/{id}")
    public ProductView byId(@PathVariable("id") long id) {
        return ProductView.of(catalogService.byId(id));
    }

    public record UpdatePriceRequest(BigDecimal unitPrice) {
    }

    /** 演示缓存失效：调价后立即驱逐缓存，下次读拿到新价。 */
    @PutMapping("/{id}/price")
    public ProductView updatePrice(@PathVariable("id") long id, @RequestBody UpdatePriceRequest request) {
        return ProductView.of(catalogService.updatePrice(id, request.unitPrice()));
    }
}
