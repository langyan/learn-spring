# Elasticsearch 企业级架构重构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有的 H2 + @Async 双写架构重构为 Kafka + MySQL + Outbox 模式的企业级 CQRS 架构

**Architecture:** CQRS + Event-Driven + Outbox Pattern。写操作通过 Spring Events 发布到 Kafka，消费者同步到 Elasticsearch。Kafka 失败时降级到 Outbox 表，定时任务重试。读操作分离：精确查询用 MySQL，全文搜索用 Elasticsearch (IK 分词器)。

**Tech Stack:** Spring Boot 3.5.0, Java 21, MySQL 8.0, Kafka 7.5.0, Elasticsearch 9.1.4, IK Analyzer, Spring Data JPA, Spring Data Elasticsearch, Spring Kafka

---

## 文件结构总览

### 新建文件

| 文件路径 | 职责 |
|---------|------|
| `src/main/java/com/lin/spring/elasticsearch/entity/Outbox.java` | Outbox 模式实体 |
| `src/main/java/com/lin/spring/elasticsearch/entity/OutboxStatus.java` | Outbox 状态枚举 |
| `src/main/java/com/lin/spring/elasticsearch/event/ProductChangedEvent.java` | Spring 内部事件 |
| `src/main/java/com/lin/spring/elasticsearch/event/ProductOperation.java` | 操作类型枚举 |
| `src/main/java/com/lin/spring/elasticsearch/event/ProductEvent.java` | Kafka 消息格式 |
| `src/main/java/com/lin/spring/elasticsearch/event/ProductData.java` | 产品数据 DTO |
| `src/main/java/com/lin/spring/elasticsearch/converter/TagsConverter.java` | JPA 属性转换器 |
| `src/main/java/com/lin/spring/elasticsearch/repository/OutboxRepository.java` | Outbox 仓储 |
| `src/main/java/com/lin/spring/elasticsearch/service/ProductWriteService.java` | 写服务（CQRS） |
| `src/main/java/com/lin/spring/elasticsearch/service/ProductReadService.java` | 读服务（MySQL） |
| `src/main/java/com/lin/spring/elasticsearch/service/ProductSearchService.java` | 搜索服务（ES） |
| `src/main/java/com/lin/spring/elasticsearch/listener/ProductEventListener.java` | 事件监听器（Kafka 发布） |
| `src/main/java/com/lin/spring/elasticsearch/consumer/ProductSyncConsumer.java` | Kafka 消费者（ES 同步） |
| `src/main/java/com/lin/spring/elasticsearch/poller/OutboxPoller.java` | Outbox 定时轮询器 |
| `src/main/java/com/lin/spring/elasticsearch/controller/OutboxController.java` | Outbox 管理控制器 |
| `src/main/java/com/lin/spring/elasticsearch/config/KafkaConfig.java` | Kafka 配置 |
| `src/main/java/com/lin/spring/elasticsearch/config/ElasticsearchConfig.java` | ES 配置 |
| `src/main/java/com/lin/spring/elasticsearch/exception/ProductNotFoundException.java` | 产品未找到异常 |
| `src/main/java/com/lin/spring/elasticsearch/exception/ErrorResponse.java` | 错误响应 DTO |
| `src/main/java/com/lin/spring/elasticsearch/exception/GlobalExceptionHandler.java` | 全局异常处理器 |
| `src/main/java/com/lin/spring/elasticsearch/dto/ProductCreateRequest.java` | 创建产品请求 |
| `src/main/java/com/lin/spring/elasticsearch/dto/ProductUpdateRequest.java` | 更新产品请求 |
| `src/main/java/com/lin/spring/elasticsearch/dto/SearchCriteria.java` | 搜索条件 |
| `src/main/java/com/lin/spring/elasticsearch/dto/PriceRangeStats.java` | 价格区间统计 |
| `src/main/java/com/lin/spring/elasticsearch/dto/SearchResultWithAggregations.java` | 搜索结果+聚合 |
| `src/main/java/com/lin/spring/elasticsearch/util/JsonUtils.java` | JSON 工具类 |
| `src/main/resources/elasticsearch/settings.json` | ES 分析器配置 |
| `docker-compose.yml` | Docker 编排文件 |

### 修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `pom.xml` | 添加 Kafka、MySQL 依赖，移除 H2 |
| `src/main/resources/application.properties` | 更新数据源、Kafka 配置 |
| `src/main/java/com/lin/spring/elasticsearch/entity/Product.java` | BigDecimal 价格、时间戳、TagsConverter |
| `src/main/java/com/lin/spring/elasticsearch/entity/ProductDocument.java` | IK 分词器配置 |
| `src/main/java/com/lin/spring/elasticsearch/controller/ProductController.java` | CQRS 风格重构 |
| `src/main/java/com/lin/spring/elasticsearch/LinSpringElasticsearchApplication.java` | 添加 @EnableScheduling |
| `src/test/resources/api.http` | 更新 API 测试 |
| `README.md` | 更新文档 |

### 删除文件

| 文件路径 | 原因 |
|---------|------|
| `src/main/java/com/lin/spring/elasticsearch/service/SyncService.java` | 被 Kafka + Outbox 替代 |
| `src/main/java/com/lin/spring/elasticsearch/service/ProductService.java` | 拆分为 Write/Read/Search 服务 |
| `src/main/java/com/lin/spring/elasticsearch/controller/SyncController.java` | 被 OutboxController 替代 |
| `src/main/java/com/lin/spring/elasticsearch/entity/SyncLog.java` | 被 Outbox 替代 |
| `src/main/java/com/lin/spring/elasticsearch/repository/SyncLogRepository.java` | 被 OutboxRepository 替代 |
| `src/main/java/com/lin/spring/elasticsearch/mapper/ProductMapper.java` | 移入 ProductSearchService |

---

## 实现任务

### Task 1: 更新 Maven 依赖

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: 添加 Kafka 依赖**

在 `<dependencies>` 节点添加：
```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

- [ ] **Step 2: 添加 MySQL 依赖**

在 `<dependencies>` 节点添加：
```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

- [ ] **Step 3: 移除 H2 依赖**

删除或注释掉：
```xml
<!--
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
-->
```

- [ ] **Step 4: 添加 Jackson 依赖（确保存在）**

确认存在：
```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

- [ ] **Step 5: 验证编译**

Run: `./mvnw clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add pom.xml
git commit -m "deps: add Kafka and MySQL dependencies, remove H2"
```

---

### Task 2: 创建 Docker Compose 配置

**Files:**
- Create: `docker-compose.yml`

- [ ] **Step 1: 创建 docker-compose.yml**

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
    image: elasticsearch:9.1.4
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

- [ ] **Step 2: 验证 YAML 语法**

Run: `docker-compose config`
Expected: 无错误输出

- [ ] **Step 3: 启动服务并验证**

Run: `docker-compose up -d`
Expected: 所有服务启动成功

- [ ] **Step 4: 安装并验证 IK 分词器**

Run:
```bash
# 安装 IK 分词器
docker exec elasticsearch elasticsearch-plugin install --batch https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-9.1.4.zip

# 重启 Elasticsearch
docker restart elasticsearch

# 等待 Elasticsearch 启动（约 30 秒）
sleep 30

# 验证 IK 分词器安装
curl -s "localhost:9200/_cat/plugins" | grep ik
```
Expected: 输出包含 `analysis-ik`

- [ ] **Step 5: 提交**

```bash
git add docker-compose.yml
git commit -m "infra: add Docker Compose for MySQL, Elasticsearch, Kafka, Zookeeper"
```

---

### Task 3: 更新 application.properties

**Files:**
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: 替换数据源配置**

将 H2 配置替换为 MySQL：
```properties
# MySQL 数据源
spring.datasource.url=jdbc:mysql://localhost:3306/productdb?useSSL=false&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

删除：
```properties
# spring.datasource.url=jdbc:h2:mem:productdb
# spring.datasource.driverClassName=org.h2.Driver
# spring.datasource.username=sa
# spring.datasource.password=
```

- [ ] **Step 2: 更新 JPA 配置**

添加：
```properties
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
```

保留：
```properties
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

- [ ] **Step 3: 添加 Kafka 配置**

```properties
# Kafka
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=com.lin.spring.elasticsearch
```

- [ ] **Step 4: 添加调度任务配置**

```properties
# 开启调度任务
spring.task.scheduling.pool.size=2
```

- [ ] **Step 5: 更新日志配置**

```properties
logging.level.org.springframework.kafka=INFO
```

删除异步执行器配置：
```properties
# 删除以下配置
# spring.task.execution.pool.core-size=5
# spring.task.execution.pool.max-size=10
# spring.task.execution.thread-name-prefix=async-sync-
```

- [ ] **Step 6: 提交**

```bash
git add src/main/resources/application.properties
git commit -m "config: update application.properties for MySQL and Kafka"
```

---

### Task 4: 创建 Outbox 实体和枚举

**Files:**
- Create: `src/main/java/com/lin/spring/elasticsearch/entity/Outbox.java`
- Create: `src/main/java/com/lin/spring/elasticsearch/entity/OutboxStatus.java`

- [ ] **Step 1: 创建 OutboxStatus 枚举**

```java
package com.lin.spring.elasticsearch.entity;

public enum OutboxStatus {
    PENDING,    // 待发送
    PUBLISHED,  // 已发送
    FAILED      // 发送失败
}
```

- [ ] **Step 2: 创建 Outbox 实体**

```java
package com.lin.spring.elasticsearch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Outbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String aggregateType;  // "Product"

    @Column(nullable = false, length = 100)
    private String aggregateId;    // product ID

    @Column(nullable = false, length = 50)
    private String eventType;      // "CREATED", "UPDATED", "DELETED"

    @Column(columnDefinition = "JSON", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;

    @Column(nullable = false)
    private Integer retryCount = 0;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = OutboxStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/entity/Outbox.java src/main/java/com/lin/spring/elasticsearch/entity/OutboxStatus.java
git commit -m "feat: add Outbox entity and status enum"
```

---

### Task 5: 创建事件类

**Files:**
- Create: `src/main/java/com/lin/spring/elasticsearch/event/ProductOperation.java`
- Create: `src/main/java/com/lin/spring/elasticsearch/event/ProductChangedEvent.java`
- Create: `src/main/java/com/lin/spring/elasticsearch/event/ProductData.java`
- Create: `src/main/java/com/lin/spring/elasticsearch/event/ProductEvent.java`

- [ ] **Step 1: 创建 ProductOperation 枚举**

```java
package com.lin.spring.elasticsearch.event;

public enum ProductOperation {
    CREATED, UPDATED, DELETED
}
```

- [ ] **Step 2: 创建 ProductData (Kafka 消息数据)**

```java
package com.lin.spring.elasticsearch.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductData {
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private List<String> tags;
}
```

- [ ] **Step 3: 创建 ProductEvent (Kafka 消息)**

```java
package com.lin.spring.elasticsearch.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("operation")
    private ProductOperation operation;

    @JsonProperty("productId")
    private Long productId;

    @JsonProperty("productData")
    private ProductData productData;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    public static ProductEvent created(ProductData data) {
        return new ProductEvent(
            UUID.randomUUID().toString(),
            ProductOperation.CREATED,
            Long.valueOf(data.getId()),
            data,
            LocalDateTime.now()
        );
    }

    public static ProductEvent updated(ProductData data) {
        return new ProductEvent(
            UUID.randomUUID().toString(),
            ProductOperation.UPDATED,
            Long.valueOf(data.getId()),
            data,
            LocalDateTime.now()
        );
    }

    public static ProductEvent deleted(Long productId) {
        return new ProductEvent(
            UUID.randomUUID().toString(),
            ProductOperation.DELETED,
            productId,
            null,
            LocalDateTime.now()
        );
    }
}
```

- [ ] **Step 4: 创建 ProductChangedEvent (Spring 内部事件)**

```java
package com.lin.spring.elasticsearch.event;

import com.lin.spring.elasticsearch.entity.Product;

import java.time.LocalDateTime;

public record ProductChangedEvent(
    Long productId,
    ProductOperation operation,
    Product product,
    LocalDateTime occurredAt
) {
    public ProductChangedEvent {
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }
}
```

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/event/
git commit -m "feat: add event classes for ProductChangedEvent and ProductEvent"
```

---

### Task 6: 更新 Product 实体

**Files:**
- Modify: `src/main/java/com/lin/spring/elasticsearch/entity/Product.java`

- [ ] **Step 1: 添加时间戳字段**

在类中添加：
```java
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@EntityListeners(AuditingEntityListener.class)
public class Product {
    // ... 现有字段 ...

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 修改 price 字段类型**

将 `Double` 改为 `BigDecimal`：
```java
import java.math.BigDecimal;

@Column(precision = 10, scale = 2)
private BigDecimal price;
```

同时更新 getter/setter 中的类型。

- [ ] **Step 3: 添加 TagsConverter 注解**

```java
@Convert(converter = TagsConverter.class)
@Column(columnDefinition = "VARCHAR(500)")
private List<String> tags;
```

- [ ] **Step 4: 验证编译**

Run: `./mvnw clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/entity/Product.java
git commit -m "refactor: update Product entity with BigDecimal, timestamps, TagsConverter"
```

---

### Task 7: 创建 TagsConverter

**Files:**
- Create: `src/main/java/com/lin/spring/elasticsearch/converter/TagsConverter.java`

- [ ] **Step 1: 创建 TagsConverter**

```java
package com.lin.spring.elasticsearch.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Converter(autoApply = false)
public class TagsConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert tags to JSON", e);
            return "[]";
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(dbData, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to convert JSON to tags: {}", dbData, e);
            return List.of();
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/converter/TagsConverter.java
git commit -m "feat: add TagsConverter for List<String> to JSON conversion"
```

---

### Task 8: 更新 ProductDocument

**Files:**
- Modify: `src/main/java/com/lin/spring/elasticsearch/entity/ProductDocument.java`

- [ ] **Step 1: 添加 IK 分词器注解**

```java
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "products")
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

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/entity/ProductDocument.java
git commit -m "feat: add IK analyzer to ProductDocument"
```

---

### Task 9: 创建 ElasticsearchConfig

**Files:**
- Create: `src/main/java/com/lin/spring/elasticsearch/config/ElasticsearchConfig.java`

- [ ] **Step 1: 创建 ElasticsearchConfig**

```java
package com.lin.spring.elasticsearch.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchOperations;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchRestTemplate;

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

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/config/ElasticsearchConfig.java
git commit -m "config: add ElasticsearchConfig with ElasticsearchClient"
```

---

### Task 10: 创建 ES settings.json

**Files:**
- Create: `src/main/resources/elasticsearch/settings.json`

- [ ] **Step 1: 创建 settings.json**

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

- [ ] **Step 2: 提交**

```bash
git add src/main/resources/elasticsearch/settings.json
git commit -m "config: add Elasticsearch settings for IK analyzer"
```

---

### Task 11: 创建 KafkaConfig

**Files:**
- Create: `src/main/java/com/lin/spring/elasticsearch/config/KafkaConfig.java`

- [ ] **Step 1: 创建 KafkaConfig**

```java
package com.lin.spring.elasticsearch.config;

import com.lin.spring.elasticsearch.event.ProductEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

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

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/config/KafkaConfig.java
git commit -m "config: add KafkaConfig for producer and consumer"
```

---

### Task 12: 创建 OutboxRepository

**Files:**
- Create: `src/main/java/com/lin/spring/elasticsearch/repository/OutboxRepository.java`

- [ ] **Step 1: 创建 OutboxRepository**

```java
package com.lin.spring.elasticsearch.repository;

import com.lin.spring.elasticsearch.entity.Outbox;
import com.lin.spring.elasticsearch.entity.OutboxStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    List<Outbox> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/repository/OutboxRepository.java
git commit -m "feat: add OutboxRepository"
```

---

### Task 13: 创建 DTO 类

**Files:**
- Create: `src/main/java/com/lin/spring/elasticsearch/dto/ProductCreateRequest.java`
- Create: `src/main/java/com/lin/spring/elasticsearch/dto/ProductUpdateRequest.java`
- Create: `src/main/java/com/lin/spring/elasticsearch/dto/SearchCriteria.java`
- Create: `src/main/java/com/lin/spring/elasticsearch/dto/PriceRangeStats.java`
- Create: `src/main/java/com/lin/spring/elasticsearch/dto/SearchResultWithAggregations.java`

- [ ] **Step 1: 创建 ProductCreateRequest**

```java
package com.lin.spring.elasticsearch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductCreateRequest {

    @NotBlank(message = "产品名称不能为空")
    private String name;

    @NotBlank(message = "产品描述不能为空")
    private String description;

    @NotNull(message = "价格不能为空")
    @Positive(message = "价格必须大于0")
    private BigDecimal price;

    @NotBlank(message = "分类不能为空")
    private String category;

    private List<String> tags;
}
```

- [ ] **Step 2: 创建 ProductUpdateRequest**

```java
package com.lin.spring.elasticsearch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductUpdateRequest {

    @NotBlank(message = "产品名称不能为空")
    private String name;

    @NotBlank(message = "产品描述不能为空")
    private String description;

    @NotNull(message = "价格不能为空")
    @Positive(message = "价格必须大于0")
    private BigDecimal price;

    @NotBlank(message = "分类不能为空")
    private String category;

    private List<String> tags;
}
```

- [ ] **Step 3: 创建 SearchCriteria**

```java
package com.lin.spring.elasticsearch.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SearchCriteria {

    private String keyword;
    private String category;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
}
```

- [ ] **Step 4: 创建 PriceRangeStats**

```java
package com.lin.spring.elasticsearch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PriceRangeStats {
    private String rangeKey;
    private Long count;
}
```

- [ ] **Step 5: 创建 SearchResultWithAggregations**

```java
package com.lin.spring.elasticsearch.dto;

import com.lin.spring.elasticsearch.entity.ProductDocument;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class SearchResultWithAggregations {
    private List<ProductDocument> products;
    private Map<String, Long> categoryCounts;
    private Double avgPrice;
    private Long totalHits;
}
```

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/dto/
git commit -m "feat: add DTOs for requests and search results"
```

---

### Task 14: 创建异常类

**Files:**
- Create: `src/main/java/com/lin/spring/elasticsearch/exception/ProductNotFoundException.java`
- Create: `src/main/java/com/lin/spring/elasticsearch/exception/ErrorResponse.java`
- Create: `src/main/java/com/lin/spring/elasticsearch/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: 创建 ProductNotFoundException**

```java
package com.lin.spring.elasticsearch.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long id) {
        super("Product not found with id: " + id);
    }

    public ProductNotFoundException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: 创建 ErrorResponse**

```java
package com.lin.spring.elasticsearch.exception;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ErrorResponse {
    private String code;
    private String message;
}
```

- [ ] **Step 3: 创建 GlobalExceptionHandler**

```java
package com.lin.spring.elasticsearch.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(ProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("PRODUCT_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        String message = errors.entrySet().stream()
            .map(e -> e.getKey() + ": " + e.getValue())
            .reduce((a, b) -> a + "; " + b)
            .orElse("验证失败");
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "服务器内部错误"));
    }
}
```

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/exception/
git commit -m "feat: add exception handling with GlobalExceptionHandler"
```

---

### Task 15: 创建工具类

**Files:**
- Create: `src/main/java/com/lin/spring/elasticsearch/util/JsonUtils.java`

- [ ] **Step 1: 创建 JsonUtils**

```java
package com.lin.spring.elasticsearch.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to JSON", e);
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize JSON to object", e);
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/util/JsonUtils.java
git commit -m "feat: add JsonUtils for JSON serialization"
```

---

### Task 16: 创建 ProductWriteService

**Files:**
- Create: `src/main/java/com/lin/spring/elasticsearch/service/ProductWriteService.java`

- [ ] **Step 1: 创建 ProductWriteService**

```java
package com.lin.spring.elasticsearch.service;

import com.lin.spring.elasticsearch.entity.Product;
import com.lin.spring.elasticsearch.event.ProductChangedEvent;
import com.lin.spring.elasticsearch.event.ProductOperation;
import com.lin.spring.elasticsearch.exception.ProductNotFoundException;
import com.lin.spring.elasticsearch.repository.ProductJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductWriteService {

    private final ProductJpaRepository productRepository;
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

        existing.setName(product.getName());
        existing.setDescription(product.getDescription());
        existing.setPrice(product.getPrice());
        existing.setCategory(product.getCategory());
        existing.setTags(product.getTags());

        Product saved = productRepository.save(existing);
        eventPublisher.publishEvent(
            new ProductChangedEvent(saved.getId(), ProductOperation.UPDATED, saved, LocalDateTime.now())
        );
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
        eventPublisher.publishEvent(
            new ProductChangedEvent(id, ProductOperation.DELETED, null, LocalDateTime.now())
        );
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/service/ProductWriteService.java
git commit -m "feat: add ProductWriteService with transactional event publishing"
```

---

### Task 17: 创建 ProductReadService

**Files:**
- Create: `src/main/java/com/lin/spring/elasticsearch/service/ProductReadService.java`

- [ ] **Step 1: 创建 ProductReadService**

```java
package com.lin.spring.elasticsearch.service;

import com.lin.spring.elasticsearch.entity.Product;
import com.lin.spring.elasticsearch.repository.ProductJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductReadService {

    private final ProductJpaRepository productRepository;

    public Optional<Product> getById(Long id) {
        return productRepository.findById(id);
    }

    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findAll(pageable);
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/service/ProductReadService.java
git commit -m "feat: add ProductReadService for MySQL queries"
```

---

### Task 18: 创建 ProductSearchService

**Files:**
- Create: `src/main/java/com/lin/spring/elasticsearch/service/ProductSearchService.java`

- [ ] **Step 1: 创建 ProductSearchService**

```java
package com.lin.spring.elasticsearch.service;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AverageAggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import com.lin.spring.elasticsearch.dto.PriceRangeStats;
import com.lin.spring.elasticsearch.dto.SearchCriteria;
import com.lin.spring.elasticsearch.dto.SearchResultWithAggregations;
import com.lin.spring.elasticsearch.entity.ProductDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    public Page<ProductDocument> search(String keyword, Pageable pageable) {
        Query query = Query.of(q -> q
            .multiMatch(m -> m
                .fields("name^2", "description^1.5", "tags", "category")
                .operator(Operator.Or)
                .fuzziness("AUTO")
                .query(keyword)));

        NativeQuery nativeQuery = NativeQuery.builder()
            .withQuery(query)
            .withPageable(pageable)
            .build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);
        return hits.map(SearchHit::getContent);
    }

    public Page<ProductDocument> advancedSearch(SearchCriteria criteria, Pageable pageable) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        // 关键词搜索
        if (StringUtils.hasText(criteria.getKeyword())) {
            boolBuilder.must(m -> m
                .multiMatch(mm -> mm
                    .fields("name^2", "description")
                    .query(criteria.getKeyword())));
        }

        // 分类过滤
        if (StringUtils.hasText(criteria.getCategory())) {
            boolBuilder.filter(f -> f
                .term(t -> t
                    .field("category")
                    .value(criteria.getCategory())));
        }

        // 价格范围过滤
        if (criteria.getMinPrice() != null || criteria.getMaxPrice() != null) {
            boolBuilder.filter(r -> r
                .range(range -> {
                    range.field("price");
                    if (criteria.getMinPrice() != null) {
                        range.gte(JsonData.of(criteria.getMinPrice()));
                    }
                    if (criteria.getMaxPrice() != null) {
                        range.lte(JsonData.of(criteria.getMaxPrice()));
                    }
                    return range;
                }));
        }

        Query query = Query.of(q -> q.bool(boolBuilder.build()));

        NativeQuery nativeQuery = NativeQuery.builder()
            .withQuery(query)
            .withPageable(pageable)
            .build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);
        return hits.map(SearchHit::getContent);
    }

    public Map<String, Long> aggregateByCategory(String keyword) {
        Query query = Query.of(q -> q
            .multiMatch(m -> m
                .fields("name", "description")
                .query(keyword)));

        NativeQuery nativeQuery = NativeQuery.builder()
            .withQuery(query)
            .withAggregation("category_count",
                Aggregation.of(a -> a
                    .terms(t -> t
                        .field("category")
                        .size(10))))
            .withMaxResults(0)
            .build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);

        return ((ElasticsearchAggregations) hits.getAggregations())
            .aggregations()
            .get("category_count")
            .sterms()
            .buckets()
            .array()
            .stream()
            .collect(Collectors.toMap(
                StringTermsBucket::key,
                StringTermsBucket::docCount
            ));
    }

    public List<PriceRangeStats> aggregateByPriceRanges(String keyword) {
        Query query = Query.of(q -> q
            .queryString(qs -> qs.query(keyword)));

        NativeQuery nativeQuery = NativeQuery.builder()
            .withQuery(query)
            .withAggregation("price_ranges",
                Aggregation.of(a -> a
                    .range(r -> r
                        .field("price")
                        .ranges(range -> range.from(0.0).to(50.0).key("0-50"),
                                range -> range.from(50.0).to(100.0).key("50-100"),
                                range -> range.from(100.0).to(200.0).key("100-200"),
                                range -> range.from(200.0).to(500.0).key("200-500"),
                                range -> range.from(500.0).key("500+")))))
            .withMaxResults(0)
            .build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);

        return ((ElasticsearchAggregations) hits.getAggregations())
            .aggregations()
            .get("price_ranges")
            .range()
            .buckets()
            .array()
            .stream()
            .map(bucket -> new PriceRangeStats(
                bucket.key(),
                bucket.docCount()
            ))
            .toList();
    }

    public SearchResultWithAggregations searchWithAggregations(String keyword, Pageable pageable) {
        Query query = Query.of(q -> q
            .multiMatch(m -> m
                .fields("name^2", "description")
                .query(keyword)));

        NativeQuery nativeQuery = NativeQuery.builder()
            .withQuery(query)
            .withAggregation("categories",
                Aggregation.of(a -> a.terms(t -> t.field("category").size(10))))
            .withAggregation("avg_price",
                Aggregation.of(a -> a.avg(AverageAggregation.of(av -> av.field("price")))))
            .withPageable(pageable)
            .build();

        SearchHits<ProductDocument> hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);

        List<ProductDocument> products = hits.getSearchHits().stream()
            .map(SearchHit::getContent)
            .toList();

        Map<String, Long> categoryCounts = ((ElasticsearchAggregations) hits.getAggregations())
            .aggregations()
            .get("categories")
            .sterms()
            .buckets()
            .array()
            .stream()
            .collect(Collectors.toMap(
                StringTermsBucket::key,
                StringTermsBucket::docCount
            ));

        Double avgPrice = ((ElasticsearchAggregations) hits.getAggregations())
            .aggregations()
            .get("avg_price")
            .avg()
            .value();

        return new SearchResultWithAggregations(
            products,
            categoryCounts,
            avgPrice,
            hits.getTotalHits()
        );
    }
}
```

说明：使用 Spring Data Elasticsearch 的 `ElasticsearchOperations` 和 `NativeQuery`，这是推荐的方式。不需要 `ElasticsearchClient` 直接注入。

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/service/ProductSearchService.java
git commit -m "feat: add ProductSearchService with NativeQuery and aggregations"
```

---

### Task 19: 创建 ProductEventListener

**Files:**
- Create: `src/main/java/com/lin/spring/elasticsearch/listener/ProductEventListener.java`

- [ ] **Step 1: 创建 ProductEventListener**

```java
package com.lin.spring.elasticsearch.listener;

import com.lin.spring.elasticsearch.entity.Outbox;
import com.lin.spring.elasticsearch.entity.OutboxStatus;
import com.lin.spring.elasticsearch.event.ProductChangedEvent;
import com.lin.spring.elasticsearch.event.ProductData;
import com.lin.spring.elasticsearch.event.ProductEvent;
import com.lin.spring.elasticsearch.event.ProductOperation;
import com.lin.spring.elasticsearch.repository.OutboxRepository;
import com.lin.spring.elasticsearch.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventListener {

    private final org.springframework.kafka.core.KafkaTemplate<String, ProductEvent> kafkaTemplate;
    private final OutboxRepository outboxRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductChanged(ProductChangedEvent event) {
        ProductEvent productEvent = switch (event.operation()) {
            case CREATED -> {
                ProductData data = toProductData(event.product());
                yield ProductEvent.created(data);
            }
            case UPDATED -> {
                ProductData data = toProductData(event.product());
                yield ProductEvent.updated(data);
            }
            case DELETED -> ProductEvent.deleted(event.productId());
        };

        try {
            kafkaTemplate.send("product-events", productEvent.getProductId().toString(), productEvent)
                .get(3, TimeUnit.SECONDS);
            log.info("Kafka event sent: productId={}, operation={}",
                productEvent.getProductId(), productEvent.getOperation());

        } catch (Exception e) {
            log.error("Failed to send Kafka event, saving to Outbox: productId={}",
                productEvent.getProductId(), e);

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

    private ProductData toProductData(com.lin.spring.elasticsearch.entity.Product product) {
        ProductData data = new ProductData();
        data.setId(product.getId().toString());
        data.setName(product.getName());
        data.setDescription(product.getDescription());
        data.setPrice(product.getPrice());
        data.setCategory(product.getCategory());
        data.setTags(product.getTags());
        return data;
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/listener/ProductEventListener.java
git commit -m "feat: add ProductEventListener with Kafka publish and Outbox fallback"
```

---

### Task 20: 创建 ProductSyncConsumer

**Files:**
- Create: `src/main/java/com/lin/spring/elasticsearch/consumer/ProductSyncConsumer.java`

- [ ] **Step 1: 创建 ProductSyncConsumer**

```java
package com.lin.spring.elasticsearch.consumer;

import com.lin.spring.elasticsearch.entity.ProductDocument;
import com.lin.spring.elasticsearch.event.ProductEvent;
import com.lin.spring.elasticsearch.event.ProductOperation;
import com.lin.spring.elasticsearch.repository.ProductElasticsearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSyncConsumer {

    private final ProductElasticsearchRepository esRepository;

    @KafkaListener(
        topics = "product-events",
        groupId = "product-sync-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeProductEvent(
            @Payload ProductEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.ACKNOWLEDGMENT) Acknowledgment acknowledgment) {

        try {
            log.info("Consuming Kafka event: productId={}, operation={}",
                event.getProductId(), event.getOperation());

            switch (event.getOperation()) {
                case CREATED, UPDATED -> {
                    ProductDocument document = toDocument(event);
                    esRepository.save(document);
                    log.info("Synced to ES: id={}, operation={}", event.getProductId(), event.getOperation());
                }
                case DELETED -> {
                    esRepository.deleteById(event.getProductId().toString());
                    log.info("Deleted from ES: id={}", event.getProductId());
                }
            }

            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }

        } catch (Exception e) {
            log.error("Failed to sync to ES: productId={}, operation={}",
                event.getProductId(), event.getOperation(), e);
            // 不 acknowledge，让 Kafka 重试
            throw e;
        }
    }

    private ProductDocument toDocument(ProductEvent event) {
        ProductDocument doc = new ProductDocument();
        doc.setId(event.getProductId().toString());
        if (event.getProductData() != null) {
            doc.setName(event.getProductData().getName());
            doc.setDescription(event.getProductData().getDescription());
            doc.setPrice(event.getProductData().getPrice());
            doc.setCategory(event.getProductData().getCategory());
            doc.setTags(event.getProductData().getTags());
        }
        return doc;
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/consumer/ProductSyncConsumer.java
git commit -m "feat: add ProductSyncConsumer for ES synchronization"
```

---

### Task 21: 创建 OutboxPoller

**Files:**
- Create: `src/main/java/com/lin/spring/elasticsearch/poller/OutboxPoller.java`

- [ ] **Step 1: 创建 OutboxPoller**

```java
package com.lin.spring.elasticsearch.poller;

import com.lin.spring.elasticsearch.entity.Outbox;
import com.lin.spring.elasticsearch.entity.OutboxStatus;
import com.lin.spring.elasticsearch.event.ProductEvent;
import com.lin.spring.elasticsearch.repository.OutboxRepository;
import com.lin.spring.elasticsearch.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

        if (pending.isEmpty()) {
            return;
        }

        log.info("Processing {} pending outbox messages", pending.size());

        for (Outbox outbox : pending) {
            try {
                ProductEvent event = JsonUtils.fromJson(outbox.getPayload(), ProductEvent.class);

                kafkaTemplate.send("product-events",
                    outbox.getAggregateId(), event)
                    .get(3, TimeUnit.SECONDS);

                outbox.setStatus(OutboxStatus.PUBLISHED);
                outbox.setProcessedAt(LocalDateTime.now());
                outboxRepository.save(outbox);

                log.info("Outbox message published: id={}", outbox.getId());

            } catch (Exception e) {
                outbox.setRetryCount(outbox.getRetryCount() + 1);

                if (outbox.getRetryCount() >= 5) {
                    outbox.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox message failed after 5 retries: id={}", outbox.getId());
                } else {
                    log.warn("Outbox message retry {}/5: id={}",
                        outbox.getRetryCount(), outbox.getId());
                }
                outboxRepository.save(outbox);
            }
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/poller/OutboxPoller.java
git commit -m "feat: add OutboxPoller for retry mechanism"
```

---

### Task 22: 创建 OutboxController

**Files:**
- Create: `src/main/java/com/lin/spring/elasticsearch/controller/OutboxController.java`

- [ ] **Step 1: 创建 OutboxController**

```java
package com.lin.spring.elasticsearch.controller;

import com.lin.spring.elasticsearch.entity.Outbox;
import com.lin.spring.elasticsearch.entity.OutboxStatus;
import com.lin.spring.elasticsearch.poller.OutboxPoller;
import com.lin.spring.elasticsearch.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            .orElseThrow(() -> new RuntimeException("Outbox not found"));
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

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/controller/OutboxController.java
git commit -m "feat: add OutboxController for outbox management"
```

---

### Task 23: 更新 ProductController

**Files:**
- Modify: `src/main/java/com/lin/spring/elasticsearch/controller/ProductController.java`

- [ ] **Step 1: 完全重写 ProductController**

```java
package com.lin.spring.elasticsearch.controller;

import com.lin.spring.elasticsearch.dto.*;
import com.lin.spring.elasticsearch.entity.Product;
import com.lin.spring.elasticsearch.entity.ProductDocument;
import com.lin.spring.elasticsearch.service.ProductReadService;
import com.lin.spring.elasticsearch.service.ProductSearchService;
import com.lin.spring.elasticsearch.service.ProductWriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductWriteService writeService;
    private final ProductReadService readService;
    private final ProductSearchService searchService;

    // ========== 写操作 ==========

    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody ProductCreateRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setTags(request.getTags());

        Product created = writeService.create(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@PathVariable Long id,
                                         @Valid @RequestBody ProductUpdateRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setTags(request.getTags());

        Product updated = writeService.update(id, product);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        writeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ========== 读操作（MySQL） ==========

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

    // ========== 搜索操作（Elasticsearch） ==========

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

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/controller/ProductController.java
git commit -m "refactor: update ProductController to CQRS style"
```

---

### Task 24: 启用 JPA Auditing 和 Scheduling

**Files:**
- Modify: `src/main/java/com/lin/spring/elasticsearch/LinSpringElasticsearchApplication.java`

- [ ] **Step 1: 添加注解**

```java
package com.lin.spring.elasticsearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class LinSpringElasticsearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinSpringElasticsearchApplication.class, args);
    }
}
```

- [ ] **Step 2: 移除 @EnableAsync（如果存在）**

删除 `@EnableAsync` 注解，因为不再需要。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/lin/spring/elasticsearch/LinSpringElasticsearchApplication.java
git commit -m "config: enable JPA Auditing and Scheduling"
```

---

### Task 25: 删除废弃文件

**Files:**
- Delete: `src/main/java/com/lin/spring/elasticsearch/service/SyncService.java`
- Delete: `src/main/java/com/lin/spring/elasticsearch/service/ProductService.java`
- Delete: `src/main/java/com/lin/spring/elasticsearch/controller/SyncController.java`
- Delete: `src/main/java/com/lin/spring/elasticsearch/entity/SyncLog.java`
- Delete: `src/main/java/com/lin/spring/elasticsearch/repository/SyncLogRepository.java`
- Delete: `src/main/java/com/lin/spring/elasticsearch/mapper/ProductMapper.java`

- [ ] **Step 1: 删除废弃文件**

```bash
git rm src/main/java/com/lin/spring/elasticsearch/service/SyncService.java
git rm src/main/java/com/lin/spring/elasticsearch/service/ProductService.java
git rm src/main/java/com/lin/spring/elasticsearch/controller/SyncController.java
git rm src/main/java/com/lin/spring/elasticsearch/entity/SyncLog.java
git rm src/main/java/com/lin/spring/elasticsearch/repository/SyncLogRepository.java
git rm src/main/java/com/lin/spring/elasticsearch/mapper/ProductMapper.java
```

- [ ] **Step 2: 提交**

```bash
git commit -m "refactor: remove deprecated files (SyncService, ProductService, SyncController, SyncLog)"
```

---

### Task 26: 更新 API 测试文件

**Files:**
- Modify: `src/test/resources/api.http`

- [ ] **Step 1: 更新 api.http**

```http
### 创建产品
POST http://localhost:8080/api/products
Content-Type: application/json

{
  "name": "无线鼠标",
  "description": "人体工学无线鼠标，精准追踪",
  "price": 99.99,
  "category": "Electronics",
  "tags": ["accessories", "wireless", "computer"]
}

### 获取产品（MySQL）
GET http://localhost:8080/api/products/1

### 获取所有产品（MySQL）
GET http://localhost:8080/api/products?page=0&size=10

### 更新产品
PUT http://localhost:8080/api/products/1
Content-Type: application/json

{
  "name": "无线鼠标",
  "description": "人体工学无线鼠标，精准追踪",
  "price": 89.99,
  "category": "Electronics",
  "tags": ["accessories", "wireless", "computer", "sale"]
}

### 删除产品
DELETE http://localhost:8080/api/products/1

### 全文搜索
GET http://localhost:8080/api/products/search?keyword=无线鼠标&page=0&size=10

### 高级搜索
POST http://localhost:8080/api/products/advanced?page=0&size=10
Content-Type: application/json

{
  "keyword": "鼠标",
  "category": "Electronics",
  "minPrice": 50,
  "maxPrice": 200
}

### 分类聚合
GET http://localhost:8080/api/products/aggregate/category?keyword=电子产品

### 价格区间聚合
GET http://localhost:8080/api/products/aggregate/price?keyword=鼠标

### 搜索+聚合（一次请求）
GET http://localhost:8080/api/products/search-with-aggregations?keyword=电子产品&page=0&size=10

### 查看 Outbox 待处理消息
GET http://localhost:8080/api/outbox/pending

### 查看 Outbox 失败消息
GET http://localhost:8080/api/outbox/failed

### 重试 Outbox 消息
POST http://localhost:8080/api/outbox/retry/1

### 删除 Outbox 消息
DELETE http://localhost:8080/api/outbox/1
```

- [ ] **Step 2: 提交**

```bash
git add src/test/resources/api.http
git commit -m "test: update api.http for new endpoints"
```

---

### Task 27: 更新 README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: 更新 README.md**

```markdown
# lin-spring-elasticsearch

Spring Boot 企业级 Elasticsearch 演示项目，采用 CQRS + Event-Driven + Outbox Pattern 架构。

## 架构

```
HTTP Request → ProductWriteService → JPA Repository → MySQL
                         ↓
                 @TransactionalEventListener
                         ↓
                 ProductEventListener → Kafka → ProductSyncConsumer → Elasticsearch
                         ↓ (Kafka 失败)
                 Outbox Table → OutboxPoller → 重试
```

## 特性

- **CQRS**: 读写分离，写操作用 MySQL，读操作用 Elasticsearch
- **Event-Driven**: Kafka 事件驱动架构
- **Outbox Pattern**: Kafka 失败时降级到 Outbox 表，定时任务重试
- **中文搜索**: IK 分词器支持中文全文检索
- **高级查询**: Elasticsearch 聚合统计和 NativeQuery

## 技术栈

- Java 21
- Spring Boot 3.5.0
- MySQL 8.0
- Kafka 7.5.0
- Elasticsearch 9.1.4
- IK Analyzer

## 快速开始

### 1. 启动基础设施

```bash
docker-compose up -d
```

### 2. 安装 IK 分词器（首次）

```bash
docker exec elasticsearch \
  elasticsearch-plugin install https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v9.1.4/elasticsearch-analysis-ik-9.1.4.zip

docker restart elasticsearch
```

### 3. 创建 ES 索引

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

### 4. 运行应用

```bash
./mvnw spring-boot:run
```

### 5. 测试 API

使用 `src/test/resources/api.http` 文件测试 API。

## API 端点

| 方法 | 路径 | 说明 | 数据源 |
|------|------|------|--------|
| POST | `/api/products` | 创建产品 | MySQL + Kafka |
| PUT | `/api/products/{id}` | 更新产品 | MySQL + Kafka |
| DELETE | `/api/products/{id}` | 删除产品 | MySQL + Kafka |
| GET | `/api/products/{id}` | 获取产品 | MySQL |
| GET | `/api/products` | 获取所有产品 | MySQL |
| GET | `/api/products/search` | 全文搜索 | ES (IK) |
| POST | `/api/products/advanced` | 高级搜索 | ES |
| GET | `/api/products/aggregate/category` | 分类聚合 | ES |
| GET | `/api/products/aggregate/price` | 价格聚合 | ES |
| GET | `/api/products/search-with-aggregations` | 搜索+聚合 | ES |
| GET | `/api/outbox/pending` | 待处理 Outbox | MySQL |
| GET | `/api/outbox/failed` | 失败 Outbox | MySQL |
| POST | `/api/outbox/retry/{id}` | 重试 Outbox | 触发轮询 |
| DELETE | `/api/outbox/{id}` | 删除 Outbox | MySQL |

## 停止

```bash
docker-compose down
```
```

- [ ] **Step 2: 提交**

```bash
git add README.md
git commit -m "docs: update README for enterprise architecture"
```

---

### Task 28: 验证编译

**Files:**
- Verify: 整个项目

- [ ] **Step 1: 清理并编译**

```bash
./mvnw clean compile
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 运行测试**

```bash
./mvnw test
```

Expected: BUILD SUCCESS（部分测试可能失败，因为需要基础设施运行）

- [ ] **Step 3: 打包**

```bash
./mvnw package -DskipTests
```

Expected: BUILD SUCCESS

---

## 执行顺序总结

按以下顺序执行任务：

1. **基础设施**: Task 1-3 (依赖、Docker、配置)
2. **实体和事件**: Task 4-8 (Outbox、Events、Product、TagsConverter)
3. **配置**: Task 9-11 (ES Config、Settings、Kafka Config)
4. **仓储和 DTO**: Task 12-15 (OutboxRepository、DTOs、异常、工具类)
5. **服务层**: Task 16-21 (Write/Read/Search 服务、EventListener、Consumer、Poller)
6. **控制器**: Task 22-23 (OutboxController、ProductController)
7. **清理**: Task 24-27 (启用注解、删除废弃文件、更新文档)

---

**总计**: 28 个任务，预计耗时 4-6 小时完成全部实现。
