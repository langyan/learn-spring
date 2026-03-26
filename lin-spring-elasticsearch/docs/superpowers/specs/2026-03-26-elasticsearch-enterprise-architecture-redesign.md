# Elasticsearch 企业级架构重构设计规格

**日期:** 2026-03-26
**状态:** 已批准
**目标:** 将现有的 H2 + @Async 双写架构重构为 Kafka + MySQL + Outbox 模式的企业级 CQRS 架构

---

## 1. 概述

### 1.1 重构目标

将 `lin-spring-elasticsearch` 从当前的原型级架构（H2 内存数据库 + Spring @Async 同步）重构为生产级企业架构：

- **消息驱动**: 使用 Kafka 实现异步事件驱动架构
- **数据持久化**: MySQL 替代 H2，支持数据持久化
- **可靠性**: Outbox 模式确保消息不丢失
- **事务安全**: Spring Events + @TransactionalEventListener 保证事务一致性
- **中文搜索**: IK 分词器支持中文全文检索
- **高级查询**: Elasticsearch 聚合统计和 NativeQuery

### 1.2 架构原则

| 原则 | 说明 |
|------|------|
| CQRS | 读写分离：写操作走 MySQL，读操作走 Elasticsearch |
| Event-Driven | 事件驱动：产品变更通过 Kafka 事件传播 |
| Transactional Safety | 事务安全：@TransactionalEventListener 确保事务提交后才发事件 |
| Reliability | 可靠性：Outbox 模式作为 Kafka 失败的降级方案 |
| Idempotency | 幂等性：ES 同步操作支持重试 |

---

## 2. 架构设计

### 2.1 整体架构图

```
┌────────────────────────────────────────────────────────────────────────┐
│                           HTTP 请求层                                   │
├────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │                     ProductController                           │    │
│  │  ┌──────────────────┐        ┌──────────────────────────────┐  │    │
│  │  │   写操作          │        │         读操作                │  │    │
│  │  │ POST/PUT/DELETE   │        │   GET /search/aggregate       │  │    │
│  │  └────────┬─────────┘        └──────────────┬───────────────┘  │    │
│  └───────────┼───────────────────────────────────┼──────────────────┘    │
│              │                               │                          │
└──────────────┼───────────────────────────────┼──────────────────────────┘
               │                               │
┌──────────────┼───────────────────────────────┼──────────────────────────┐
│              ▼                               ▼                          │
│  ┌──────────────────────┐      ┌──────────────────────────────────┐    │
│  │  ProductWriteService │      │    ProductSearchService          │    │
│  │  (写服务)             │      │    (ES 搜索服务)                 │    │
│  └──────────┬───────────┘      └─────────────┬────────────────────┘    │
│             │                                 │                         │
└─────────────┼─────────────────────────────────┼─────────────────────────┘
              │                                 │
┌─────────────┼─────────────────────────────────┼─────────────────────────┐
│              │                                 │                         │
│  ┌───────────▼─────────────────────────────────▼─────────────────────┐  │
│  │                    @TransactionalEventListener                       │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                               │                                          │
│  ┌───────────────────────────▼─────────────────────────────────────┐    │
│  │                    ProductChangedEvent                            │    │
│  └───────────────────────────┬─────────────────────────────────────┘    │
│                              │                                          │
│  ┌───────────────────────────▼─────────────────────────────────────┐    │
│  │                   ProductEventListener                            │    │
│  │  ┌─────────────────────────────────────────────────────────┐    │    │
│  │  │  KafkaTemplate.send("product-events")                   │    │    │
│  │  │         │                                                │    │    │
│  │  │         ├─→ 成功 ──────────► Kafka Topic                 │    │    │
│  │  │         │                                                │    │    │
│  │  │         └─→ 失败 ──────────► OutboxRepository.save()     │    │    │
│  │  └─────────────────────────────────────────────────────────┘    │    │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐   │
│  │                     数据存储层                                      │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐ │   │
│  │  │    MySQL     │  │  Kafka       │  │     Elasticsearch        │ │   │
│  │  │  products    │  │  product-    │  │     products index       │ │   │
│  │  │  outbox      │  │  events      │  │   (IK analyzer)          │ │   │
│  │  └──────────────┘  └──────┬───────┘  └───────────┬──────────────┘ │   │
│  └──────────────────────────────────┼──────────────────┼──────────────┘   │
│                                     │                  │                  │
│  ┌──────────────────────────────────┼──────────────────┼──────────────┐   │
│  │         ProductSyncConsumer  ◄───┘                  │              │   │
│  │         (Kafka 消费者)                              │              │   │
│  │                  │                                  │              │   │
│  │                  └──────────────────────────────────┘              │   │
│  └────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────┐   │
│  │                     OutboxPoller                                    │   │
│  │              (定时轮询 Outbox 表重试)                                │   │
│  └────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 数据流

**写入流程**:
```
HTTP Request
    → ProductWriteService
    → @Transactional
        → productRepository.save() → MySQL
        → eventPublisher.publishEvent(ProductChangedEvent)
    → 事务提交
    → @TransactionalEventListener
    → ProductEventListener
        → KafkaTemplate.send() → Kafka
        → (失败) → OutboxRepository.save() → Outbox 表
    → ProductSyncConsumer (Kafka)
        → esRepository.save() → Elasticsearch
```

**查询流程**:
- 精确查询: `ProductReadService → MySQL`
- 全文搜索: `ProductSearchService → Elasticsearch (IK 分词)`
- 聚合统计: `ProductSearchService → Elasticsearch Aggregations`

---

## 3. 核心组件

### 3.1 实体层 (Entity)

#### 3.1.1 Product (JPA Entity)

```java
@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    private String category;

    @Convert(converter = TagsConverter.class)
    private List<String> tags;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
```

**说明**:
- 主键使用自增 Long 类型
- 价格使用 `BigDecimal` 精确存储
- 标签使用 AttributeConverter 存储 JSON
- 使用 `@CreatedDate` 和 `@LastModifiedDate` 自动管理时间戳

#### 3.1.2 ProductDocument (ES Document)

```java
@Document(indexName = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument {
    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String name;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String description;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Keyword)
    private List<String> tags;
}
```

**说明**:
- ID 为 String 类型（ES 要求）
- 使用 IK 分词器：`ik_max_word` 用于索引，`ik_smart` 用于搜索
- 价格存储为 Double 类型
- 分类和标签使用 Keyword 类型用于精确过滤

#### 3.1.3 Outbox (Outbox 模式)

```java
@Entity
@Table(name = "outbox")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Outbox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aggregateType;  // "Product"
    private String aggregateId;    // product ID
    private String eventType;      // "ProductCreated", "ProductUpdated", "ProductDeleted"

    @Column(columnDefinition = "JSON")
    private String payload;

    private OutboxStatus status;   // PENDING, PUBLISHED, FAILED
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private Integer retryCount = 0;
}

public enum OutboxStatus {
    PENDING, PUBLISHED, FAILED
}
```

**说明**:
- 用于 Kafka 发送失败时的降级存储
- `payload` 存储 JSON 格式的 ProductEvent
- `retryCount` 控制重试次数，超过 5 次标记为 FAILED

### 3.2 事件层 (Event)

#### 3.2.1 ProductChangedEvent (Spring Event)

```java
public record ProductChangedEvent(
    Long productId,
    ProductOperation operation,
    Product product,
    LocalDateTime occurredAt
) {}
```

**说明**:
- Spring 内部事件，用于事务协调
- 在事务提交后通过 `@TransactionalEventListener` 发布到 Kafka

#### 3.2.2 ProductEvent (Kafka Message)

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductEvent {
    private String eventId;        // UUID
    private ProductOperation operation;
    private Long productId;
    private ProductData productData;
    private LocalDateTime timestamp;

    public static ProductEvent created(Product product) { ... }
    public static ProductEvent updated(Product product) { ... }
    public static ProductEvent deleted(Long productId) { ... }
}

@Data
public class ProductData {
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private List<String> tags;
}
```

**说明**:
- Kafka 消息格式，支持序列化/反序列化
- `eventId` 为 UUID 用于消息去重
- 工厂方法简化创建

#### 3.2.3 ProductOperation (Enum)

```java
public enum ProductOperation {
    CREATED, UPDATED, DELETED
}
```

### 3.3 服务层 (Service)

#### 3.3.1 ProductWriteService

```java
@Service
@RequiredArgsConstructor
public class ProductWriteService {

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Product create(Product product) {
        Product saved = productRepository.save(product);
        eventPublisher.publishEvent(
            new ProductChangedEvent(saved.getId(), ProductOperation.CREATED, saved, LocalDateTime.now())
        );
        return saved;
    }

    @Transactional
    public Product update(Long id, Product product) {
        Product existing = productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
        // 更新字段...
        Product saved = productRepository.save(existing);
        eventPublisher.publishEvent(
            new ProductChangedEvent(saved.getId(), ProductOperation.UPDATED, saved, LocalDateTime.now())
        );
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        productRepository.deleteById(id);
        eventPublisher.publishEvent(
            new ProductChangedEvent(id, ProductOperation.DELETED, null, LocalDateTime.now())
        );
    }
}
```

**说明**:
- 所有写操作都在 `@Transactional` 事务中执行
- 先保存到 MySQL，再发布 Spring 事件
- 事件发布在事务内，但实际执行在事务提交后（AFTER_COMMIT）

#### 3.3.2 ProductEventListener

```java
@Component
@RequiredArgsConstructor
public class ProductEventListener {

    private final KafkaTemplate<String, ProductEvent> kafkaTemplate;
    private final OutboxRepository outboxRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductChanged(ProductChangedEvent event) {
        ProductEvent productEvent = switch (event.operation()) {
            case CREATED -> ProductEvent.created(event.product());
            case UPDATED -> ProductEvent.updated(event.product());
            case DELETED -> ProductEvent.deleted(event.productId());
        };

        try {
            kafkaTemplate
                .send("product-events", productEvent.getProductId().toString(), productEvent)
                .get(3, TimeUnit.SECONDS);

        } catch (Exception e) {
            // 降级到 Outbox
            Outbox outbox = new Outbox();
            outbox.setAggregateType("Product");
            outbox.setAggregateId(event.productId().toString());
            outbox.setEventType(event.operation().name());
            outbox.setPayload(JsonUtils.toJson(productEvent));
            outbox.setStatus(OutboxStatus.PENDING);
            outboxRepository.save(outbox);
        }
    }
}
```

**说明**:
- `@TransactionalEventListener(phase = AFTER_COMMIT)` 确保事务提交后才执行
- Kafka 发送失败时自动降级到 Outbox
- 使用同步发送（`.get()`）确保可靠性

#### 3.3.3 ProductSyncConsumer

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSyncConsumer {

    private final ProductElasticsearchRepository esRepository;
    private final ProductMapper productMapper;

    @KafkaListener(
        topics = "product-events",
        groupId = "product-sync-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeProductEvent(ProductEvent event) {
        try {
            switch (event.getOperation()) {
                case CREATED, UPDATED -> {
                    ProductDocument document = productMapper.toDocument(event.getProductData());
                    esRepository.save(document);
                    log.info("同步产品到 ES: id={}, operation={}", event.getProductId(), event.getOperation());
                }
                case DELETED -> {
                    esRepository.deleteById(event.getProductId().toString());
                    log.info("从 ES 删除产品: id={}", event.getProductId());
                }
            }
        } catch (Exception e) {
            log.error("同步到 ES 失败: productId={}, operation={}",
                event.getProductId(), event.getOperation(), e);
            throw e; // 触发 Kafka 重试
        }
    }
}
```

**说明**:
- Kafka 消费者，监听 `product-events` 主题
- 消费失败抛出异常触发 Kafka 自动重试
- 手动提交模式（MANUAL_IMMEDIATE）确保消息不丢失

#### 3.3.4 OutboxPoller

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, ProductEvent> kafkaTemplate;

    @Scheduled(fixedDelay = 30000) // 每 30 秒执行一次
    @Transactional
    public void processPendingOutbox() {
        List<Outbox> pending = outboxRepository.findByStatusOrderByCreatedAtAsc(
            OutboxStatus.PENDING, PageRequest.of(0, 100)
        );

        for (Outbox outbox : pending) {
            try {
                ProductEvent event = JsonUtils.fromJson(outbox.getPayload(), ProductEvent.class);

                kafkaTemplate.send("product-events",
                    outbox.getAggregateId(), event)
                    .get(3, TimeUnit.SECONDS);

                outbox.setStatus(OutboxStatus.PUBLISHED);
                outbox.setProcessedAt(LocalDateTime.now());
                outboxRepository.save(outbox);

                log.info("Outbox 消息重试成功: id={}", outbox.getId());

            } catch (Exception e) {
                outbox.setRetryCount(outbox.getRetryCount() + 1);

                if (outbox.getRetryCount() >= 5) {
                    outbox.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox 消息重试失败，达到最大次数: id={}", outbox.getId());
                }
                outboxRepository.save(outbox);
            }
        }
    }
}
```

**说明**:
- 定时任务，每 30 秒扫描一次 Outbox 表
- 批量处理（每次 100 条）避免内存压力
- 重试 5 次后标记为 FAILED，需要人工介入

#### 3.3.5 ProductSearchService

```java
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final ElasticsearchClient elasticsearchClient;

    /**
     * 多字段全文搜索（带权重提升）
     */
    public Page<ProductDocument> search(String keyword, Pageable pageable) {
        NativeQuery query = NativeQuery.builder()
            .withQuery(QueryBuilders.multiMatch()
                .fields("name^2", "description^1.5", "tags", "category")
                .operator(Operator.OR)
                .fuzziness("AUTO")
                .value(keyword)
                .build())
            .withPageable(pageable)
            .build();

        return elasticsearchClient.search(query, ProductDocument.class);
    }

    /**
     * 范围查询 + 多条件组合
     */
    public Page<ProductDocument> advancedSearch(SearchCriteria criteria, Pageable pageable) {
        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        if (StringUtils.hasText(criteria.keyword())) {
            boolQuery.must(QueryBuilders.multiMatch()
                .fields("name^2", "description")
                .query(criteria.keyword())
                .build()._toQuery());
        }

        if (StringUtils.hasText(criteria.category())) {
            boolQuery.filter(QueryBuilders.term()
                .field("category")
                .value(criteria.category())
                .build()._toQuery());
        }

        if (criteria.minPrice() != null || criteria.maxPrice() != null) {
            var rangeQuery = QueryBuilders.range().field("price");
            if (criteria.minPrice() != null) {
                rangeQuery.gte(JsonData.of(criteria.minPrice()));
            }
            if (criteria.maxPrice() != null) {
                rangeQuery.lte(JsonData.of(criteria.maxPrice()));
            }
            boolQuery.filter(rangeQuery.build()._toQuery());
        }

        NativeQuery query = NativeQuery.builder()
            .withQuery(boolQuery.build()._toQuery())
            .withPageable(pageable)
            .build();

        return elasticsearchClient.search(query, ProductDocument.class);
    }

    /**
     * 分类聚合统计
     */
    public Map<String, Long> aggregateByCategory(String keyword) {
        NativeQuery query = NativeQuery.builder()
            .withQuery(QueryBuilders.multiMatch()
                .fields("name", "description")
                .query(keyword)
                .build()._toQuery())
            .withAggregation("category_count",
                Aggregation.of(a -> a
                    .terms(t -> t
                        .field("category")
                        .size(10))))
            .withMaxResults(0)
            .build();

        SearchResponse<ProductDocument> response =
            elasticsearchClient.search(query, ProductDocument.class);

        return response.aggregations()
            .get("category_count")
            .sterms()
            .buckets().array().stream()
            .collect(Collectors.toMap(
                bucket -> bucket.key(),
                bucket -> bucket.docCount()
            ));
    }

    /**
     * 价格区间聚合
     */
    public List<PriceRangeStats> aggregateByPriceRanges(String keyword) {
        NativeQuery query = NativeQuery.builder()
            .withQuery(QueryBuilders.queryString(q -> q.query(keyword)))
            .withAggregation("price_ranges",
                Aggregation.of(a -> a
                    .range(r -> r
                        .field("price")
                        .ranges(range -> range.from(0).to(50).key("0-50"),
                                range -> range.from(50).to(100).key("50-100"),
                                range -> range.from(100).to(200).key("100-200"),
                                range -> range.from(200).to(500).key("200-500"),
                                range -> range.from(500).key("500+")))))
            .withMaxResults(0)
            .build();

        SearchResponse<ProductDocument> response =
            elasticsearchClient.search(query, ProductDocument.class);

        return response.aggregations()
            .get("price_ranges")
            .range()
            .buckets().array().stream()
            .map(bucket -> new PriceRangeStats(
                bucket.key(),
                bucket.docCount()
            ))
            .toList();
    }

    /**
     * 同时获取搜索结果和聚合统计
     */
    public SearchResultWithAggregations searchWithAggregations(String keyword, Pageable pageable) {
        NativeQuery query = NativeQuery.builder()
            .withQuery(QueryBuilders.multiMatch()
                .fields("name^2", "description")
                .query(keyword)
                .build())
            .withAggregation("categories",
                Aggregation.of(a -> a.terms(t -> t.field("category").size(10))))
            .withAggregation("avg_price",
                Aggregation.of(a -> a.avg(Average.of(av -> av.field("price")))))
            .withPageable(pageable)
            .build();

        SearchResponse<ProductDocument> response =
            elasticsearchClient.search(query, ProductDocument.class);

        return new SearchResultWithAggregations(
            response.hits().searchHits().stream()
                .map(SearchHit::getContent)
                .toList(),
            extractCategoryAggregation(response),
            extractAvgPrice(response),
            response.hits().total().value()
        );
    }
}
```

**说明**:
- 使用 `ElasticsearchClient` (新版 API) 而非 Repository
- 支持多字段搜索、范围查询、聚合统计
- IK 分词器自动应用于中文字段
- 一次请求可同时返回搜索结果和聚合数据

### 3.4 控制器层 (Controller)

#### 3.4.1 ProductController

```java
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductWriteService writeService;
    private final ProductReadService readService;
    private final ProductSearchService searchService;

    // 写操作
    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody ProductCreateRequest request) {
        Product product = writeService.create(request.toEntity());
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@PathVariable Long id,
                                         @Valid @RequestBody ProductUpdateRequest request) {
        Product product = writeService.update(id, request.toEntity());
        return ResponseEntity.ok(product);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        writeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // 读操作（MySQL）
    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        return readService.getById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<Page<Product>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(readService.findAll(PageRequest.of(page, size)));
    }

    // 搜索操作（Elasticsearch）
    @GetMapping("/search")
    public ResponseEntity<Page<ProductDocument>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(searchService.search(keyword, PageRequest.of(page, size)));
    }

    @PostMapping("/advanced")
    public ResponseEntity<Page<ProductDocument>> advancedSearch(
            @RequestBody SearchCriteria criteria,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(searchService.advancedSearch(criteria, PageRequest.of(page, size)));
    }

    @GetMapping("/aggregate/category")
    public ResponseEntity<Map<String, Long>> aggregateByCategory(
            @RequestParam String keyword) {
        return ResponseEntity.ok(searchService.aggregateByCategory(keyword));
    }

    @GetMapping("/aggregate/price")
    public ResponseEntity<List<PriceRangeStats>> aggregateByPrice(
            @RequestParam String keyword) {
        return ResponseEntity.ok(searchService.aggregateByPriceRanges(keyword));
    }

    @GetMapping("/search-with-aggregations")
    public ResponseEntity<SearchResultWithAggregations> searchWithAggregations(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(
            searchService.searchWithAggregations(keyword, PageRequest.of(page, size))
        );
    }
}
```

**说明**:
- 明确的读写分离：写操作用 `writeService`，读操作用 `readService`，搜索用 `searchService`
- RESTful 风格的 URL 设计
- 支持分页、聚合等多种查询方式

#### 3.4.2 OutboxController

```java
@RestController
@RequestMapping("/api/outbox")
@RequiredArgsConstructor
public class OutboxController {

    private final OutboxRepository outboxRepository;
    private final OutboxPoller outboxPoller;

    @GetMapping("/pending")
    public ResponseEntity<List<Outbox>> getPending() {
        return ResponseEntity.ok(
            outboxRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, 50)
            )
        );
    }

    @GetMapping("/failed")
    public ResponseEntity<List<Outbox>> getFailed() {
        return ResponseEntity.ok(
            outboxRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.FAILED, PageRequest.of(0, 50)
            )
        );
    }

    @PostMapping("/retry/{id}")
    public ResponseEntity<Void> retry(@PathVariable Long id) {
        Outbox outbox = outboxRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Outbox not found"));
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setRetryCount(0);
        outboxRepository.save(outbox);
        outboxPoller.processPendingOutbox();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        outboxRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
```

**说明**:
- 提供 Outbox 消息的管理接口
- 支持手动重试失败的消息
- 支持清理无效消息

### 3.5 配置层 (Configuration)

#### 3.5.1 KafkaConfig

```java
@Configuration
@EnableKafka
public class KafkaConfig {

    @Bean
    public ProducerFactory<String, ProductEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, ProductEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ConsumerFactory<String, ProductEvent> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "product-sync-group");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(config,
            new StringDeserializer(),
            new JsonDeserializer<>(ProductEvent.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProductEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ProductEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
```

#### 3.5.2 ElasticsearchConfig

```java
@Configuration
public class ElasticsearchConfig {

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        RestClient restClient = RestClient.builder(
            new HttpHost("localhost", 9200, "http")
        ).build();

        ElasticsearchTransport transport = new RestClientTransport(
            restClient,
            new JacksonJsonpMapper()
        );

        return new ElasticsearchClient(transport);
    }

    @Bean
    public ElasticsearchOperations elasticsearchOperations() {
        return new ElasticsearchRestTemplate(elasticsearchClient());
    }
}
```

#### 3.5.3 application.properties

```properties
# 应用配置
spring.application.name=lin-spring-elasticsearch
server.port=8080

# MySQL 数据源
spring.datasource.url=jdbc:mysql://localhost:3306/productdb?useSSL=false&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

# Elasticsearch
spring.elasticsearch.uris=http://localhost:9200

# Kafka
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=com.lin.spring.elasticsearch

# 开启调度任务
spring.task.scheduling.pool.size=2

# 日志
logging.level.org.springframework.kafka=INFO
logging.level.com.lin.spring.elasticsearch=DEBUG
```

#### 3.5.4 docker-compose.yml

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: mysql-es
    environment:
      MYSQL_ROOT_PASSWORD: password
      MYSQL_DATABASE: productdb
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql

  elasticsearch:
    image: elasticsearch:8.12.0
    container_name: elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
      - "9300:9300"
    volumes:
      - es-data:/usr/share/elasticsearch/data
      - ./elasticsearch/plugins:/usr/share/elasticsearch/plugins

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: kafka
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports:
      - "2181:2181"

volumes:
  mysql-data:
  es-data:
```

#### 3.5.5 elasticsearch/settings.json

```json
{
  "index": {
    "analysis": {
      "analyzer": {
        "ik_max_word_analyzer": {
          "type": "custom",
          "tokenizer": "ik_max_word"
        },
        "ik_smart_analyzer": {
          "type": "custom",
          "tokenizer": "ik_smart"
        }
      }
    }
  }
}
```

### 3.6 依赖项 (pom.xml)

```xml
<!-- 新增依赖 -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>

<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- 移除 H2 依赖 -->
<!-- <dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
</dependency> -->
```

---

## 4. API 接口规范

### 4.1 写操作接口

| 方法 | 路径 | 说明 | 事件 |
|------|------|------|------|
| POST | `/api/products` | 创建产品 | ProductCreated |
| PUT | `/api/products/{id}` | 更新产品 | ProductUpdated |
| DELETE | `/api/products/{id}` | 删除产品 | ProductDeleted |

### 4.2 读操作接口

| 方法 | 路径 | 说明 | 数据源 |
|------|------|------|--------|
| GET | `/api/products/{id}` | 获取单个产品 | MySQL |
| GET | `/api/products` | 分页获取所有产品 | MySQL |

### 4.3 搜索接口

| 方法 | 路径 | 说明 | 数据源 |
|------|------|------|--------|
| GET | `/api/products/search` | 全文搜索 | ES (IK) |
| POST | `/api/products/advanced` | 高级搜索 | ES |
| GET | `/api/products/aggregate/category` | 分类聚合 | ES |
| GET | `/api/products/aggregate/price` | 价格聚合 | ES |
| GET | `/api/products/search-with-aggregations` | 搜索+聚合 | ES |

### 4.4 管理接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/outbox/pending` | 获取待处理 Outbox |
| GET | `/api/outbox/failed` | 获取失败 Outbox |
| POST | `/api/outbox/retry/{id}` | 重试 Outbox 消息 |
| DELETE | `/api/outbox/{id}` | 删除 Outbox 消息 |

---

## 5. 部署和启动

### 5.1 启动顺序

1. **启动基础设施**:
```bash
docker-compose up -d mysql elasticsearch kafka zookeeper
```

2. **安装 IK 分词器**（首次）:
```bash
docker exec elasticsearch \
  elasticsearch-plugin install https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v8.12.0/elasticsearch-analysis-ik-8.12.0.zip

docker restart elasticsearch
```

3. **创建 ES 索引**:
```bash
curl -X PUT "localhost:9200/products" -H 'Content-Type: application/json' -d'
{
  "settings": {
    "analysis": {
      "analyzer": {
        "ik_max_word_analyzer": {
          "type": "custom",
          "tokenizer": "ik_max_word"
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "name": {
        "type": "text",
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart"
      },
      "description": {
        "type": "text",
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart"
      }
    }
  }
}
'
```

4. **运行应用**:
```bash
./mvnw spring-boot:run
```

### 5.2 停止顺序

1. 停止应用
2. 停止 Docker 服务: `docker-compose down`

---

## 6. 测试策略

### 6.1 单元测试

- **ProductWriteServiceTest**: 测试事务和事件发布
- **ProductEventListenerTest**: 测试 Kafka 发送和 Outbox 降级
- **ProductSyncConsumerTest**: 测试 Kafka 消费和 ES 同步
- **ProductSearchServiceTest**: 测试各种查询和聚合

### 6.2 集成测试

使用 `@SpringBootTest` 和 Testcontainers 进行端到端测试。

### 6.3 API 测试

使用 `api.http` 文件进行手动测试。

---

## 7. 错误处理

### 7.1 异常类型

| 异常 | HTTP 状态 | 说明 |
|------|-----------|------|
| ProductNotFoundException | 404 | 产品不存在 |
| ValidationException | 400 | 请求参数验证失败 |
| KafkaException | 503 | Kafka 不可用（自动降级到 Outbox） |
| ElasticsearchException | 503 | ES 不可用 |

### 7.2 全局异常处理器

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    // 见上文设计
}
```

---

## 8. 监控和运维

### 8.1 关键指标

- Kafka 消息吞吐量
- Outbox 待处理数量
- ES 同步延迟
- API 响应时间

### 8.2 日志

- 产品变更日志
- Kafka 发送/消费日志
- ES 同步日志
- Outbox 处理日志

---

## 9. 后续优化

1. **死信队列**: Kafka 消费失败多次后进入死信队列
2. **指标采集**: 集成 Micrometer + Prometheus
3. **分布式追踪**: 集成 OpenTelemetry
4. **缓存**: Redis 缓存热门产品
5. **事件溯源**: 完整的事件存储

---

**变更历史**:
- 2026-03-26: 初始版本创建
