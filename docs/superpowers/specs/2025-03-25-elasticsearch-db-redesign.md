# lin-spring-elasticsearch Architecture Redesign

**Date:** 2025-03-25
**Module:** lin-spring-elasticsearch
**Type:** Architecture Redesign

## Overview

Redesign the Elasticsearch module to use a dual-storage architecture with H2 database as the primary data store and Elasticsearch as the search index. Data synchronization uses async double-write pattern with status tracking.

## Goals

1. **Data Persistence** - Store Product data reliably in H2 database
2. **Search Performance** - Leverage Elasticsearch for full-text search
3. **Async Sync** - Use Spring @Async for non-blocking ES updates
4. **Failure Tracking** - Track sync status for recovery

## Architecture

### Overall Architecture

```
┌─────────────┐
│  Controller │
└──────┬──────┘
       │
┌──────▼──────┐
│   Service   │
└──────┬──────┘
       │
       ├─────────────► JPA Repository (H2) ──► Product 表
       │                                    │
       │                                    └──► SyncLog 表
       │
       └─── @Async ───► ES Repository ──────► Elasticsearch Index
```

### Data Flow

**Write Operations:**
1. Service receives request
2. JPA Repository saves to H2 (synchronous, within transaction)
3. Service triggers async sync to Elasticsearch
4. Async method saves to ES and updates SyncLog status

**Read Operations:**
- Search: Read from Elasticsearch
- By ID: Read from H2 database
- List All: Read from H2 database

**Error Handling:**
- ES sync failure: Log to SyncLog with FAILED status
- Retry mechanism: Manual retry via API endpoint

## Data Models

### Product Entity (JPA)

```java
@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private Double price;
    private String category;
    private String tags;  // JSON string storage
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### ProductDocument (Elasticsearch)

```java
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

### SyncLog Entity (JPA)

```java
@Entity
@Table(name = "sync_logs")
public class SyncLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;
    private String operation;      // CREATE, UPDATE, DELETE
    private String status;         // PENDING, SUCCESS, FAILED
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

## Components

### Repository Layer

**JPA Repositories:**
- `ProductJpaRepository` - CRUD for Product entity
- `SyncLogRepository` - CRUD for SyncLog entity

**Elasticsearch Repository:**
- `ProductElasticsearchRepository` - Search operations

### Service Layer

**ProductService Methods:**
```java
@Transactional
public Product create(Product product)
@Transactional
public Product update(Long id, Product product)
@Transactional
public void delete(Long id)
public Product getById(Long id)
public List<Product> getAll()
public Page<ProductDocument> search(String keyword, Pageable pageable)
public Page<ProductDocument> advancedSearch(...)
@Async
public void syncToElasticsearch(Product product, String operation)
```

**SyncService Methods:**
```java
public List<SyncLog> getFailedSyncs()
public void retrySync(Long syncLogId)
public void updateSyncStatus(Long productId, String operation, String status, String error)
```

### Controller Layer

**ProductController Endpoints:**
- `POST /api/products` - Create (DB + async ES)
- `GET /api/products/{id}` - Get by ID (from DB)
- `GET /api/products` - List all (from DB)
- `PUT /api/products/{id}` - Update (DB + async ES)
- `DELETE /api/products/{id}` - Delete (DB + async ES)
- `GET /api/products/search` - Search (from ES)
- `GET /api/products/advanced` - Advanced search (from ES)

**SyncController Endpoints:**
- `GET /api/sync/logs` - Get all sync logs
- `GET /api/sync/logs/failed` - Get failed syncs
- `POST /api/sync/retry/{id}` - Retry failed sync

## Configuration

### Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
</dependency>
```

### application.properties

```properties
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
```

## Error Handling

### Sync Failure Scenarios

1. **Elasticsearch Unavailable**
   - Log to SyncLog with FAILED status
   - Store error message
   - Allow manual retry via API

2. **Mapping Errors**
   - Log specific validation/mapping error
   - Don't block database transaction

3. **Retry Logic**
   - Manual retry via `/api/sync/retry/{id}`
   - Increment retryCount on each attempt
   - Max retry limit: 3 attempts

## Implementation Strategy

### Phase 1: Database Layer
1. Add JPA and H2 dependencies
2. Create Product entity with JPA annotations
3. Create ProductJpaRepository
4. Create SyncLog entity and repository
5. Configure H2 database

### Phase 2: Service Layer Refactor
1. Create mapper between Product and ProductDocument
2. Implement @Async sync method
3. Add sync status tracking
4. Refactor CRUD methods for dual-write

### Phase 3: Controller Updates
1. Update endpoints to use Long IDs
2. Add SyncController
3. Update API test file

### Phase 4: Testing
1. Test successful sync flow
2. Test sync failure handling
3. Test retry mechanism
4. Verify data consistency

## Acceptance Criteria

- [ ] Product data saved to H2 database
- [ ] Product data asynchronously synced to Elasticsearch
- [ ] Sync status tracked in SyncLog table
- [ ] Failed syncs can be retried via API
- [ ] Search operations read from Elasticsearch
- [ ] CRUD operations read from H2 database
- [ ] @Async configuration working correctly
- [ ] Error handling and logging functional

## Migration Notes

**Breaking Changes:**
- Product ID changes from `String` to `Long`
- Entity structure changes
- API contracts may need adjustment

**Rollback Plan:**
- Keep previous implementation in git history
- Can revert commits if needed
