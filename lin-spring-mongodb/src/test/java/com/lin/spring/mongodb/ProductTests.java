package com.lin.spring.mongodb;


import com.lin.spring.mongodb.entity.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ProductTests {
    @Autowired
    private WebTestClient webTestClient;

    @Test
    void testGetAllProducts() {
        webTestClient.get().uri("/api/products")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Product.class);
    }
}