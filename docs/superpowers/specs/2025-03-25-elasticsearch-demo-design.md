# lin-spring-elasticsearch Design Document

**Date:** 2025-03-25
**Module:** lin-spring-elasticsearch
**Author:** Claude Code

## Overview

A new Spring Boot module demonstrating Elasticsearch integration using Spring Data Elasticsearch with Traditional MVC architecture. The demo showcases basic CRUD operations and full-text search capabilities on a Product domain.

## Goals

1. Demonstrate basic CRUD operations with Elasticsearch
2. Showcase full-text search with multi-field queries
3. Provide a clean, idiomatic Spring Boot example following existing project patterns
4. Include Docker Compose setup for local Elasticsearch instance

## Architecture

### Technology Stack

| Component | Version |
|-----------|---------|
| Spring Boot | 3.5.0 |
| Java | 21 |
| Spring Data Elasticsearch | (managed by Boot) |
| Lombok | (managed by Boot) |
| Elasticsearch | 8.12.0 (Docker) |

### Project Structure

```
lin-spring-elasticsearch/
├── pom.xml
├── docker-compose.yml
├── src/main/java/com/lin/spring/elasticsearch/
│   ├── LinSpringElasticsearchApplication.java
│   ├── entity/
│   │   └── Product.java
│   ├── repository/
│   │   └── ProductRepository.java
│   ├── service/
│   │   └── ProductService.java
│   └── controller/
│       └── ProductController.java
├── src/main/resources/
│   ├── application.properties
│   └── data.json
└── src/test/resources/
    └── api.http
```

## Data Model

### Product Entity

```java
@Document(indexName = "products")
public class Product {
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

**Field Types Explained:**
- **Text**: Analyzed for full-text search (name, description)
- **Keyword**: Exact match, aggregations (category, tags)
- **Double**: Numeric range queries (price)
- **Date**: Time-based queries (createdAt)

## API Design

### REST Endpoints

| Method | Path | Description | Request Body |
|--------|------|-------------|--------------|
| POST | `/api/products` | Create product | Product |
| GET | `/api/products/{id}` | Get by ID | - |
| GET | `/api/products` | Get all (paginated) | - |
| PUT | `/api/products/{id}` | Update product | Product |
| DELETE | `/api/products/{id}` | Delete product | - |
| GET | `/api/products/search` | Full-text search | keyword, page, size |
| GET | `/api/products/advanced` | Filtered search | category, minPrice, maxPrice |

### Search Operations

**Basic Search:**
- Keyword-based search across name and description fields
- Pagination support

**Advanced Search:**
- Filter by category
- Price range filtering (min/max)
- Pagination and sorting

## Configuration

### application.properties

```properties
spring.application.name=lin-spring-elasticsearch
server.port=8080

spring.elasticsearch.uris=http://localhost:9200
# Security disabled in Docker for development; credentials for future production use
# spring.elasticsearch.username=elastic
# spring.elasticsearch.password=changeme

logging.level.org.springframework.data.elasticsearch=DEBUG
```

### Docker Compose

```yaml
version: '3.8'
services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.0
    container_name: lin-elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    ports:
      - "9200:9200"
      - "9300:9300"
    volumes:
      - es-data:/usr/share/elasticsearch/data
volumes:
  es-data:
```

## Implementation Approach

**Selected Approach:** Repository-Only with Query Methods

This approach leverages Spring Data Elasticsearch's query derivation mechanism, which is:
- Idiomatic to Spring ecosystem
- Minimal boilerplate code
- Easy to understand and maintain
- Consistent with other modules in the project

### Repository Interface

```java
public interface ProductRepository extends ElasticsearchRepository<Product, String> {
    List<Product> findByName(String name);
    List<Product> findByCategory(String category);
    List<Product> findByPriceBetween(Double min, Double max);

    Page<Product> findByNameContainingOrDescriptionContaining(
        String name, String description, Pageable pageable
    );

    Page<Product> findByCategoryAndPriceLessThanEqual(
        String category, Double maxPrice, Pageable pageable
    );
}
```

## Dependencies

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-devtools</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Development Workflow

1. Start Elasticsearch: `docker-compose up -d`
2. Run application: `./mvnw spring-boot:run`
3. Test APIs using `src/test/resources/api.http`

## Testing

### Sample Test Requests

```http
### Create Product
POST http://localhost:8080/api/products
Content-Type: application/json

{
  "name": "Wireless Headphones",
  "description": "High-quality noise-cancelling headphones",
  "price": 99.99,
  "category": "Electronics",
  "tags": ["audio", "wireless", "bluetooth"]
}

### Search Products
GET http://localhost:8080/api/products/search?keyword=wireless&page=0&size=10

### Advanced Search
GET http://localhost:8080/api/products/advanced?category=Electronics&minPrice=50&maxPrice=200&page=0&size=10
```

## Acceptance Criteria

- [ ] All CRUD operations working correctly
- [ ] Full-text search returns relevant results
- [ ] Advanced search with filters works
- [ ] Pagination functions properly
- [ ] Docker Compose starts Elasticsearch successfully
- [ ] Code follows existing project patterns
