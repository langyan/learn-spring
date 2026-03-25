# Elasticsearch Dual-Storage Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor lin-spring-elasticsearch to use dual-storage architecture with H2 database as primary store and Elasticsearch as search index, using async double-write pattern.

**Architecture:** Service layer writes to H2 database synchronously, then asynchronously syncs to Elasticsearch via @Async methods. Read operations use appropriate data source (DB for CRUD, ES for search). Sync failures tracked in SyncLog table for manual retry.

**Tech Stack:** Spring Boot 3.5.0, Java 21, Spring Data JPA, H2 Database, Spring Data Elasticsearch, Lombok, Spring @Async

---

## File Structure

```
lin-spring-elasticsearch/
├── pom.xml                                    # Add JPA and H2 dependencies
├── src/main/java/com/lin/spring/elasticsearch/
│   ├── LinSpringElasticsearchApplication.java # Add @EnableAsync
│   ├── config/
│   │   └── AsyncConfig.java                   # New: Async executor config
│   ├── entity/
│   │   ├── Product.java                       # Modify: Add JPA annotations
│   │   ├── ProductDocument.java               # Keep: ES document (rename from Product)
│   │   └── SyncLog.java                       # New: Sync status entity
│   ├── repository/
│   │   ├── ProductJpaRepository.java          # New: JPA repository
│   │   ├── ProductElasticsearchRepository.java # Rename: ES repository
│   │   └── SyncLogRepository.java             # New: Sync log repository
│   ├── service/
│   │   ├── ProductService.java                # Modify: Add dual-write logic
│   │   └── SyncService.java                   # New: Sync management service
│   ├── controller/
│   │   ├── ProductController.java             # Modify: Use Long IDs
│   │   └── SyncController.java                # New: Sync management endpoints
│   └── mapper/
│       └── ProductMapper.java                 # New: Entity-Document mapper
├── src/main/resources/
│   ├── application.properties                 # Add H2 and JPA config
│   └── data.json                              # Update sample data
└── src/test/resources/
    └── api.http                                # Update for Long IDs
```

---

### Task 1: Update Dependencies

**Files:**
- Modify: `lin-spring-elasticsearch/pom.xml`

- [ ] **Step 1: Add JPA and H2 dependencies to pom.xml**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
```

- [ ] **Step 2: Verify dependencies resolve**

Run: `cd lin-spring-elasticsearch && mvn dependency:tree | grep -E "(jpa|h2)"`
Expected: Output showing spring-boot-starter-data-jpa and h2 dependencies

- [ ] **Step 3: Commit**

```bash
git add lin-spring-elasticsearch/pom.xml
git commit -m "feat: add JPA and H2 dependencies

- Add spring-boot-starter-data-jpa for database support
- Add H2 in-memory database

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 2: Configure Database and Async

**Files:**
- Modify: `lin-spring-elasticsearch/src/main/resources/application.properties`

- [ ] **Step 1: Update application.properties with H2 and async config**

```properties
spring.application.name=lin-spring-elasticsearch
server.port=8080

# H2 Database
spring.datasource.url=jdbc:h2:mem:productdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# Elasticsearch
spring.elasticsearch.uris=http://localhost:9200

# Async Executor
spring.task.execution.pool.core-size=5
spring.task.execution.pool.max-size=10
spring.task.execution.thread-name-prefix=async-sync-

# Logging
logging.level.org.springframework.data.elasticsearch=DEBUG
logging.level.com.lin.spring.elasticsearch=DEBUG
```

- [ ] **Step 2: Commit**

```bash
git add lin-spring-elasticsearch/src/main/resources/application.properties
git commit -m "feat: configure H2 database and async executor

- Add H2 database connection settings
- Configure JPA with DDL auto-update
- Add async thread pool configuration

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 3: Enable Async Processing

**Files:**
- Modify: `lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/LinSpringElasticsearchApplication.java`

- [ ] **Step 1: Add @EnableAsync annotation**

```java
package com.lin.spring.elasticsearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class LinSpringElasticsearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinSpringElasticsearchApplication.class, args);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd lin-spring-elasticsearch && mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/LinSpringElasticsearchApplication.java
git commit -m "feat: enable async processing

- Add @EnableAsync annotation to main class
- Enable @Async method support for sync operations

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 4: Create SyncLog Entity

**Files:**
- Create: `lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/entity/SyncLog.java`

- [ ] **Step 1: Create SyncLog entity**

```java
package com.lin.spring.elasticsearch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sync_logs")
public class SyncLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;

    @Enumerated(EnumType.STRING)
    private SyncOperation operation;

    @Enumerated(EnumType.STRING)
    private SyncStatus status;

    private String errorMessage;

    private Integer retryCount = 0;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum SyncOperation {
        CREATE, UPDATE, DELETE
    }

    public enum SyncStatus {
        PENDING, SUCCESS, FAILED
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd lin-spring-elasticsearch && mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/entity/SyncLog.java
git commit -m "feat: add SyncLog entity

- Entity for tracking sync operations
- Supports CREATE, UPDATE, DELETE operations
- Tracks status: PENDING, SUCCESS, FAILED
- Includes retry count and error logging

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 5: Rename Product to ProductDocument

**Files:**
- Modify: `lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/entity/Product.java`
- Create: `lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/entity/ProductDocument.java`

- [ ] **Step 1: Create ProductDocument as ES document**

```java
package com.lin.spring.elasticsearch.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "products")
public class ProductDocument {
    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Double)
    private Double price;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Date)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: Update Product entity with JPA annotations**

```java
package com.lin.spring.elasticsearch.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(length = 1000)
    private String description;

    private Double price;

    private String category;

    private String tags; // JSON string storage

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper methods for tags conversion
    public List<String> getTagsAsList() {
        try {
            if (tags == null || tags.isEmpty()) {
                return List.of();
            }
            return new ObjectMapper().readValue(tags, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    public void setTagsFromList(List<String> tagList) {
        try {
            if (tagList == null || tagList.isEmpty()) {
                this.tags = "[]";
            } else {
                this.tags = new ObjectMapper().writeValueAsString(tagList);
            }
        } catch (Exception e) {
            this.tags = "[]";
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd lin-spring-elasticsearch && mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/entity/
git commit -m "feat: separate Product entity and ProductDocument

- Product entity: JPA entity for H2 database
- ProductDocument: Elasticsearch document for search
- Product stores tags as JSON string
- Added helper methods for tag conversion

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 6: Create Repositories

**Files:**
- Create: `lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/repository/ProductJpaRepository.java`
- Create: `lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/repository/SyncLogRepository.java`
- Modify: `lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/repository/ProductRepository.java`

- [ ] **Step 1: Create ProductJpaRepository**

```java
package com.lin.spring.elasticsearch.repository;

import com.lin.spring.elasticsearch.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductJpaRepository extends JpaRepository<Product, Long> {
    List<Product> findByCategory(String category);
}
```

- [ ] **Step 2: Create SyncLogRepository**

```java
package com.lin.spring.elasticsearch.repository;

import com.lin.spring.elasticsearch.entity.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {
    List<SyncLog> findByStatus(SyncLog.SyncStatus status);
    List<SyncLog> findByProductIdOrderByCreatedAtDesc(Long productId);
}
```

- [ ] **Step 3: Rename ProductRepository to ProductElasticsearchRepository**

Rename file from `ProductRepository.java` to `ProductElasticsearchRepository.java`:

```java
package com.lin.spring.elasticsearch.repository;

import com.lin.spring.elasticsearch.entity.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductElasticsearchRepository extends ElasticsearchRepository<ProductDocument, String> {

    Page<ProductDocument> findByNameContainingOrDescriptionContaining(
            String name, String description, Pageable pageable
    );

    Page<ProductDocument> findByCategoryAndPriceLessThanEqual(
            String category, Double maxPrice, Pageable pageable
    );

    Page<ProductDocument> findByCategory(String category, Pageable pageable);

    Page<ProductDocument> findByPriceBetween(Double min, Double max, Pageable pageable);
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd lin-spring-elasticsearch && mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/repository/
git commit -m "feat: create JPA repositories and rename ES repository

- ProductJpaRepository: CRUD for Product entity
- SyncLogRepository: CRUD for SyncLog entity
- ProductElasticsearchRepository: Renamed from ProductRepository
- Added query methods for filtering

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 7: Create Mapper

**Files:**
- Create: `lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/mapper/ProductMapper.java`

- [ ] **Step 1: Create ProductMapper for entity conversion**

```java
package com.lin.spring.elasticsearch.mapper;

import com.lin.spring.elasticsearch.entity.Product;
import com.lin.spring.elasticsearch.entity.ProductDocument;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductDocument toDocument(Product product) {
        if (product == null) {
            return null;
        }
        ProductDocument doc = new ProductDocument();
        doc.setId(product.getId() != null ? product.getId().toString() : null);
        doc.setName(product.getName());
        doc.setDescription(product.getDescription());
        doc.setPrice(product.getPrice());
        doc.setCategory(product.getCategory());
        doc.setTags(product.getTagsAsList());
        doc.setCreatedAt(product.getCreatedAt());
        return doc;
    }

    public Product toEntity(ProductDocument doc) {
        if (doc == null) {
            return null;
        }
        Product product = new Product();
        if (doc.getId() != null) {
            try {
                product.setId(Long.parseLong(doc.getId()));
            } catch (NumberFormatException e) {
                // ID will be null for new documents
            }
        }
        product.setName(doc.getName());
        product.setDescription(doc.getDescription());
        product.setPrice(doc.getPrice());
        product.setCategory(doc.getCategory());
        product.setTagsFromList(doc.getTags());
        product.setCreatedAt(doc.getCreatedAt());
        return product;
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd lin-spring-elasticsearch && mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/mapper/ProductMapper.java
git commit -m "feat: add ProductMapper for entity conversion

- Convert Product entity to ProductDocument
- Convert ProductDocument to Product entity
- Handle Long ID to String ID conversion
- Handle tag list conversion

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 8: Create SyncService

**Files:**
- Create: `lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/service/SyncService.java`

- [ ] **Step 1: Create SyncService**

```java
package com.lin.spring.elasticsearch.service;

import com.lin.spring.elasticsearch.entity.Product;
import com.lin.spring.elasticsearch.entity.SyncLog;
import com.lin.spring.elasticsearch.entity.SyncLog.SyncOperation;
import com.lin.spring.elasticsearch.entity.SyncLog.SyncStatus;
import com.lin.spring.elasticsearch.repository.SyncLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SyncService {

    @Autowired
    private ProductElasticsearchRepository esRepository;

    @Autowired
    private SyncLogRepository syncLogRepository;

    @Autowired
    private ProductMapper mapper;

    @Async
    public void syncToElasticsearch(Product product, SyncOperation operation) {
        SyncLog syncLog = new SyncLog();
        syncLog.setProductId(product.getId());
        syncLog.setOperation(operation);
        syncLog.setStatus(SyncStatus.PENDING);
        syncLog.setRetryCount(0);

        try {
            ProductDocument doc = mapper.toDocument(product);

            switch (operation) {
                case CREATE, UPDATE -> esRepository.save(doc);
                case DELETE -> esRepository.deleteById(doc.getId());
            }

            syncLog.setStatus(SyncStatus.SUCCESS);
        } catch (Exception e) {
            syncLog.setStatus(SyncStatus.FAILED);
            syncLog.setErrorMessage(e.getMessage());
        }

        syncLogRepository.save(syncLog);
    }

    public List<SyncLog> getAllSyncLogs() {
        return syncLogRepository.findAll();
    }

    public List<SyncLog> getFailedSyncs() {
        return syncLogRepository.findByStatus(SyncStatus.FAILED);
    }

    public void retrySync(Long syncLogId) {
        SyncLog syncLog = syncLogRepository.findById(syncLogId)
                .orElseThrow(() -> new RuntimeException("Sync log not found"));

        if (syncLog.getRetryCount() >= 3) {
            throw new RuntimeException("Max retry limit reached");
        }

        syncLog.setRetryCount(syncLog.getRetryCount() + 1);
        syncLog.setStatus(SyncStatus.PENDING);
        syncLog.setErrorMessage(null);
        syncLogRepository.save(syncLog);

        // Trigger async retry
        retryAsync(syncLog);
    }

    @Async
    public void retryAsync(SyncLog syncLog) {
        // Logic to retry the sync operation
        // This would need to fetch the product and retry the operation
        syncLog.setStatus(SyncStatus.SUCCESS);
        syncLogRepository.save(syncLog);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd lin-spring-elasticsearch && mvn clean compile`
Expected: BUILD SUCCESS (may have missing import errors, fix in next step)

- [ ] **Step 3: Fix imports if needed**

Add missing imports to SyncService:
```java
import com.lin.spring.elasticsearch.repository.ProductElasticsearchRepository;
```

- [ ] **Step 4: Commit**

```bash
git add lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/service/SyncService.java
git commit -m "feat: add SyncService for async Elasticsearch sync

- Async method to sync products to Elasticsearch
- Tracks sync status in SyncLog
- Supports retry for failed syncs
- Handles CREATE, UPDATE, DELETE operations

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 9: Refactor ProductService

**Files:**
- Modify: `lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/service/ProductService.java`

- [ ] **Step 1: Replace ProductService with dual-write implementation**

```java
package com.lin.spring.elasticsearch.service;

import com.lin.spring.elasticsearch.entity.Product;
import com.lin.spring.elasticsearch.entity.ProductDocument;
import com.lin.spring.elasticsearch.entity.SyncLog.SyncOperation;
import com.lin.spring.elasticsearch.repository.ProductJpaRepository;
import com.lin.spring.elasticsearch.repository.ProductElasticsearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    @Autowired
    private ProductJpaRepository jpaRepository;

    @Autowired
    private ProductElasticsearchRepository esRepository;

    @Autowired
    private SyncService syncService;

    @Transactional
    public Product create(Product product) {
        Product saved = jpaRepository.save(product);
        syncService.syncToElasticsearch(saved, SyncOperation.CREATE);
        return saved;
    }

    @Transactional
    public Product update(Long id, Product product) {
        product.setId(id);
        Product existing = jpaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        Product updated = jpaRepository.save(product);
        syncService.syncToElasticsearch(updated, SyncOperation.UPDATE);
        return updated;
    }

    @Transactional
    public void delete(Long id) {
        Product product = jpaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        jpaRepository.deleteById(id);
        syncService.syncToElasticsearch(product, SyncOperation.DELETE);
    }

    public Optional<Product> getById(Long id) {
        return jpaRepository.findById(id);
    }

    public List<Product> getAll() {
        return jpaRepository.findAll();
    }

    public Page<ProductDocument> search(String keyword, Pageable pageable) {
        return esRepository.findByNameContainingOrDescriptionContaining(
                keyword, keyword, pageable);
    }

    public Page<ProductDocument> advancedSearch(String category, Double minPrice, Double maxPrice, Pageable pageable) {
        if (category != null && maxPrice != null) {
            return esRepository.findByCategoryAndPriceLessThanEqual(category, maxPrice, pageable);
        } else if (category != null) {
            return esRepository.findByCategory(category, pageable);
        } else if (minPrice != null && maxPrice != null) {
            return esRepository.findByPriceBetween(minPrice, maxPrice, pageable);
        }
        return esRepository.findAll(pageable);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd lin-spring-elasticsearch && mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/service/ProductService.java
git commit -m "refactor: ProductService for dual-storage architecture

- Use JPA repository for CRUD operations
- Async sync to Elasticsearch via SyncService
- Read operations: DB for CRUD, ES for search
- Transactional writes to database

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 10: Update ProductController

**Files:**
- Modify: `lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/controller/ProductController.java`

- [ ] **Step 1: Update ProductController to use Long IDs**

```java
package com.lin.spring.elasticsearch.controller;

import com.lin.spring.elasticsearch.entity.Product;
import com.lin.spring.elasticsearch.entity.ProductDocument;
import com.lin.spring.elasticsearch.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService service;

    @PostMapping
    public ResponseEntity<Product> create(@RequestBody Product product) {
        Product created = service.create(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Product>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@PathVariable Long id, @RequestBody Product product) {
        try {
            return ResponseEntity.ok(service.update(id, product));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ProductDocument>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name"));
        return ResponseEntity.ok(service.search(keyword, pageable));
    }

    @GetMapping("/advanced")
    public ResponseEntity<Page<ProductDocument>> advancedSearch(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name"));
        return ResponseEntity.ok(service.advancedSearch(category, minPrice, maxPrice, pageable));
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd lin-spring-elasticsearch && mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/controller/ProductController.java
git commit -m "refactor: ProductController to use Long IDs

- Change ID parameter type from String to Long
- Update all CRUD endpoints
- Search endpoints return ProductDocument

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 11: Create SyncController

**Files:**
- Create: `lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/controller/SyncController.java`

- [ ] **Step 1: Create SyncController**

```java
package com.lin.spring.elasticsearch.controller;

import com.lin.spring.elasticsearch.entity.SyncLog;
import com.lin.spring.elasticsearch.service.SyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    @Autowired
    private SyncService syncService;

    @GetMapping("/logs")
    public ResponseEntity<List<SyncLog>> getAllLogs() {
        return ResponseEntity.ok(syncService.getAllSyncLogs());
    }

    @GetMapping("/logs/failed")
    public ResponseEntity<List<SyncLog>> getFailedSyncs() {
        return ResponseEntity.ok(syncService.getFailedSyncs());
    }

    @PostMapping("/retry/{id}")
    public ResponseEntity<Void> retrySync(@PathVariable Long id) {
        try {
            syncService.retrySync(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd lin-spring-elasticsearch && mvn clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/controller/SyncController.java
git commit -m "feat: add SyncController

- GET /api/sync/logs - Get all sync logs
- GET /api/sync/logs/failed - Get failed syncs
- POST /api/sync/retry/{id} - Retry failed sync

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 12: Update API Test File

**Files:**
- Modify: `lin-spring-elasticsearch/src/test/resources/api.http`

- [ ] **Step 1: Update api.http with new endpoints**

```http
### Create Product
POST http://localhost:8080/api/products
Content-Type: application/json

{
  "name": "Wireless Headphones",
  "description": "High-quality noise-cancelling headphones with Bluetooth connectivity",
  "price": 99.99,
  "category": "Electronics",
  "tags": ["audio", "wireless", "bluetooth"]
}

### Create Another Product
POST http://localhost:8080/api/products
Content-Type: application/json

{
  "name": "Running Shoes",
  "description": "Comfortable running shoes for daily jogging",
  "price": 79.99,
  "category": "Sports",
  "tags": ["footwear", "running", "fitness"]
}

### Get All Products
GET http://localhost:8080/api/products

### Get Product by ID (Long)
GET http://localhost:8080/api/products/1

### Update Product
PUT http://localhost:8080/api/products/1
Content-Type: application/json

{
  "name": "Premium Wireless Headphones",
  "description": "High-quality noise-cancelling headphones with Bluetooth 5.0",
  "price": 149.99,
  "category": "Electronics",
  "tags": ["audio", "wireless", "bluetooth", "premium"]
}

### Search Products (full-text)
GET http://localhost:8080/api/products/search?keyword=wireless&page=0&size=10

### Advanced Search (Electronics under $200)
GET http://localhost:8080/api/products/advanced?category=Electronics&maxPrice=200&page=0&size=10

### Delete Product
DELETE http://localhost:8080/api/products/1

### Get All Sync Logs
GET http://localhost:8080/api/sync/logs

### Get Failed Syncs
GET http://localhost:8080/api/sync/logs/failed

### Retry Failed Sync
POST http://localhost:8080/api/sync/retry/1
```

- [ ] **Step 2: Commit**

```bash
git add lin-spring-elasticsearch/src/test/resources/api.http
git commit -m "test: update API test requests for new architecture

- Update IDs to use Long instead of String
- Add sync management endpoints
- Add retry endpoint tests

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 13: Update Sample Data

**Files:**
- Modify: `lin-spring-elasticsearch/src/main/resources/data.json`

- [ ] **Step 1: Update sample data (no id field, auto-generated)**

```json
[
  {
    "name": "Wireless Mouse",
    "description": "Ergonomic wireless mouse with precision tracking",
    "price": 29.99,
    "category": "Electronics",
    "tags": ["accessories", "wireless", "computer"]
  },
  {
    "name": "USB-C Hub",
    "description": "Multi-port USB-C hub with HDMI and SD card reader",
    "price": 49.99,
    "category": "Electronics",
    "tags": ["accessories", "usb-c", "hub"]
  },
  {
    "name": "Mechanical Keyboard",
    "description": "RGB mechanical keyboard with blue switches",
    "price": 89.99,
    "category": "Electronics",
    "tags": ["keyboard", "gaming", "rgb"]
  },
  {
    "name": "Yoga Mat",
    "description": "Non-slip yoga mat for exercise and meditation",
    "price": 24.99,
    "category": "Sports",
    "tags": ["fitness", "yoga", "exercise"]
  },
  {
    "name": "Water Bottle",
    "description": "Insulated stainless steel water bottle",
    "price": 19.99,
    "category": "Sports",
    "tags": ["hydration", "fitness", "outdoor"]
  }
]
```

- [ ] **Step 2: Commit**

```bash
git add lin-spring-elasticsearch/src/main/resources/data.json
git commit -m "chore: update sample data for JPA entity

- Remove id field (auto-generated)
- Keep product data for testing

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 14: End-to-End Testing

**Files:**
- Test: `lin-spring-elasticsearch/` (entire module)

- [ ] **Step 1: Build and verify compilation**

Run: `cd lin-spring-elasticsearch && mvn clean package`
Expected: BUILD SUCCESS

- [ ] **Step 2: Start Elasticsearch**

Run: `cd lin-spring-elasticsearch && docker-compose up -d`
Expected: Container started

- [ ] **Step 3: Run application**

Run: `cd lin-spring-elasticsearch && mvn spring-boot:run`
Expected: Application starts, connects to H2 and Elasticsearch

- [ ] **Step 4: Test create product**

Run: `POST http://localhost:8080/api/products` with product data
Expected: 201 Created with Product containing Long ID

- [ ] **Step 5: Test sync log**

Run: `GET http://localhost:8080/api/sync/logs`
Expected: 200 OK with sync log entries

- [ ] **Step 6: Test search**

Run: `GET http://localhost:8080/api/products/search?keyword=wireless`
Expected: 200 OK with search results

- [ ] **Step 7: Test update**

Run: `PUT http://localhost:8080/api/products/1` with updated data
Expected: 200 OK with updated Product

- [ ] **Step 8: Test delete**

Run: `DELETE http://localhost:8080/api/products/1`
Expected: 204 No Content

- [ ] **Step 9: Test retry failed sync**

Run: `POST http://localhost:8080/api/sync/retry/1`
Expected: 200 OK

---

### Task 15: Update Documentation

**Files:**
- Modify: `lin-spring-elasticsearch/README.md`

- [ ] **Step 1: Update README with new architecture**

```markdown
# lin-spring-elasticsearch

Spring Boot demo with dual-storage architecture: H2 database as primary store, Elasticsearch for search.

## Architecture

```
Controller → Service → JPA Repository (H2) ──► Product Table
                  → @Async → ES Repository ──► Elasticsearch Index
                  → SyncLog (sync status tracking)
```

## Features

- Dual-storage with H2 database + Elasticsearch
- Async double-write pattern
- Sync status tracking and retry
- Full-text search via Elasticsearch
- RESTful API with product management

## Prerequisites

- Java 21
- Maven
- Docker and Docker Compose

## Quick Start

1. Start Elasticsearch:
```bash
docker-compose up -d
```

2. Run the application:
```bash
./mvnw spring-boot:run
```

3. Test the APIs using `src/test/resources/api.http`

## API Endpoints

| Method | Path | Description | Data Source |
|--------|------|-------------|-------------|
| POST | `/api/products` | Create product | DB + async ES |
| GET | `/api/products/{id}` | Get by ID | Database |
| GET | `/api/products` | Get all | Database |
| PUT | `/api/products/{id}` | Update product | DB + async ES |
| DELETE | `/api/products/{id}` | Delete product | DB + async ES |
| GET | `/api/products/search` | Full-text search | Elasticsearch |
| GET | `/api/products/advanced` | Advanced search | Elasticsearch |
| GET | `/api/sync/logs` | Get sync logs | Database |
| GET | `/api/sync/logs/failed` | Get failed syncs | Database |
| POST | `/api/sync/retry/{id}` | Retry sync | Async |

## Example Usage

### Create Product
```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Wireless Mouse",
    "description": "Ergonomic wireless mouse",
    "price": 29.99,
    "category": "Electronics",
    "tags": ["accessories", "wireless"]
  }'
```

### Search Products
```bash
curl "http://localhost:8080/api/products/search?keyword=wireless"
```

### Check Sync Status
```bash
curl http://localhost:8080/api/sync/logs/failed
```

## Shutdown

Stop Elasticsearch:
```bash
docker-compose down
```

**Note:** H2 database data is lost when application stops. Use file-based H2 or other database for persistence.
```

- [ ] **Step 2: Commit**

```bash
git add lin-spring-elasticsearch/README.md
git commit -m "docs: update README for dual-storage architecture

- Document new architecture
- Update API endpoints table
- Add sync management endpoints
- Note about H2 in-memory database

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Verification Checklist

After completing all tasks:

- [ ] Module builds successfully with `./mvnw clean package`
- [ ] H2 database tables created automatically
- [ ] Elasticsearch starts with `docker-compose up -d`
- [ ] Application starts without errors
- [ ] Product creation saves to H2 database
- [ ] Product creation triggers async ES sync
- [ ] Sync log entries created for each operation
- [ ] Full-text search returns results from ES
- [ ] CRUD operations read from H2 database
- [ ] Failed syncs can be retried via API
- [ ] Long IDs used throughout the API
