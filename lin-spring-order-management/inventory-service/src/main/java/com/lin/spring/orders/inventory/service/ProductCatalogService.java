package com.lin.spring.orders.inventory.service;

import com.lin.spring.orders.inventory.domain.Product;
import com.lin.spring.orders.inventory.repository.InventoryRepository;
import com.lin.spring.orders.inventory.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 产品目录：Redis cache-aside（@Cacheable + TTL 60s + 更新时驱逐）。
 *
 * <p>文章第 10 节原则：缓存用于性能，不作正确性来源——库存与支付决策一律以 PostgreSQL 为准。
 * 价格被 order-service 读取用于服务端定价；缓存短暂过期最多导致读到旧价（再次下单即新价），
 * 而订单一旦创建，单价即快照在 order_items，不受后续调价影响。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCatalogService {

    public static final String CACHE_PRODUCTS = "products";

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    @Cacheable(cacheNames = CACHE_PRODUCTS, key = "#productId")
    public Product byId(long productId) {
        log.debug("Cache miss for product {}, loading from PostgreSQL", productId);
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    public List<Product> all() {
        return productRepository.findAllByOrderByProductIdAsc();
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_PRODUCTS, key = "#productId")
    public Product updatePrice(long productId, BigDecimal newPrice) {
        if (newPrice == null || newPrice.signum() < 0) {
            throw new IllegalArgumentException("Price must be >= 0");
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        BigDecimal old = product.getUnitPrice();
        product.setUnitPrice(newPrice);
        log.info("Product {} price updated {} -> {} (cache evicted)", productId, old, newPrice);
        return product;
    }

    /** 补货（演示管理操作，直接作用于权威库存表）。 */
    @Transactional
    public int restock(long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
        int updated = inventoryRepository.restock(productId, quantity);
        log.info("Restocked product {} +{}", productId, quantity);
        return updated;
    }

    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(long productId) {
            super("Product not found: " + productId);
        }
    }
}
