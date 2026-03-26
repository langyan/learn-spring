package com.lin.spring.elasticsearch.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexInitializer {

    private final ElasticsearchClient elasticsearchClient;

    private static final String PRODUCTS_INDEX = "products";

    @EventListener(ApplicationReadyEvent.class)
    public void createIndicesIfNotExists() {
        createProductsIndex();
    }

    private void createProductsIndex() {
        try {
            boolean exists = elasticsearchClient
                .indices()
                .exists(ExistsRequest.of(e -> e.index(PRODUCTS_INDEX)))
                .value();

            if (!exists) {
                log.info("Creating Elasticsearch index: {}", PRODUCTS_INDEX);

                CreateIndexResponse response = elasticsearchClient
                    .indices()
                    .create(c -> c.index(PRODUCTS_INDEX));

                if (response.acknowledged()) {
                    log.info("Successfully created index: {}", PRODUCTS_INDEX);
                } else {
                    log.warn("Index creation not acknowledged: {}", PRODUCTS_INDEX);
                }
            } else {
                log.info("Index {} already exists", PRODUCTS_INDEX);
            }
        } catch (ElasticsearchException | IOException e) {
            log.error("Failed to create index: {}", PRODUCTS_INDEX, e);
        }
    }
}
