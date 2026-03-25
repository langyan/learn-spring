# lin-spring-elasticsearch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a Spring Boot module demonstrating Elasticsearch integration with CRUD operations and full-text search on a Product entity.

**Architecture:** Traditional Spring MVC with Spring Data Elasticsearch Repositories using query derivation. Single-module Maven project following existing project patterns with layered architecture (Entity → Repository → Service → Controller).

**Tech Stack:** Spring Boot 3.5.0, Java 21, Spring Data Elasticsearch, Lombok, Docker Compose for Elasticsearch 8.12.0

---

## File Structure

```
lin-spring-elasticsearch/
├── pom.xml                                    # Maven configuration with dependencies
├── docker-compose.yml                         # Elasticsearch container definition
├── src/main/java/com/lin/spring/elasticsearch/
│   ├── LinSpringElasticsearchApplication.java # Main application class
│   ├── entity/Product.java                    # Document entity with @Document
│   ├── repository/ProductRepository.java      # ElasticsearchRepository interface
│   ├── service/ProductService.java            # Business logic layer
│   └── controller/ProductController.java      # REST endpoints
├── src/main/resources/
│   ├── application.properties                 # ES connection configuration
│   └── data.json                              # Sample product data for testing
└── src/test/resources/api.http                # API test requests
```

---

### Task 1: Create Project Structure and pom.xml

**Files:**
- Create: `lin-spring-elasticsearch/pom.xml`

- [ ] **Step 1: Create the module directory**

Run: `mkdir -p lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/{entity,repository,service,controller}`
Run: `mkdir -p lin-spring-elasticsearch/src/main/resources`
Run: `mkdir -p lin-spring-elasticsearch/src/test/resources`

Expected: Directory structure created

- [ ] **Step 2: Create pom.xml with dependencies**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.0</version>
        <relativePath/>
    </parent>
    <groupId>com.lin.spring.elasticsearch</groupId>
    <artifactId>lin-spring-elasticsearch</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>lin-spring-elasticsearch</name>
    <description>Elasticsearch Demo Project for Spring Boot</description>
    <properties>
        <java.version>21</java.version>
    </properties>
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
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Verify Maven can compile**

Run: `cd lin-spring-elasticsearch && ./mvnw clean compile`
Expected: BUILD SUCCESS with Maven wrapper downloaded and project compiled

- [ ] **Step 4: Commit**

```bash
git add lin-spring-elasticsearch/
git commit -m "feat: create elasticsearch module with pom.xml

- Add Spring Boot 3.5.0 with Java 21
- Include spring-boot-starter-data-elasticsearch
- Add Lombok, DevTools, and validation dependencies

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 2: Create Docker Compose Configuration

**Files:**
- Create: `lin-spring-elasticsearch/docker-compose.yml`

- [ ] **Step 1: Create docker-compose.yml**

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

- [ ] **Step 2: Verify Docker Compose syntax**

Run: `cd lin-spring-elasticsearch && docker-compose config`
Expected: Valid YAML output without errors

- [ ] **Step 3: Commit**

```bash
git add lin-spring-elasticsearch/docker-compose.yml
git commit -m "feat: add docker-compose for Elasticsearch

- Elasticsearch 8.12.0 with single-node configuration
- Security disabled for development
- Persistent volume for data storage

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 3: Create Application Configuration

**Files:**
- Create: `lin-spring-elasticsearch/src/main/resources/application.properties`

- [ ] **Step 1: Create application.properties**

```properties
spring.application.name=lin-spring-elasticsearch
server.port=8080

# Elasticsearch configuration
spring.elasticsearch.uris=http://localhost:9200

# Logging
logging.level.org.springframework.data.elasticsearch=DEBUG
```

- [ ] **Step 2: Commit**

```bash
git add lin-spring-elasticsearch/src/main/resources/application.properties
git commit -m "feat: add application configuration

- Configure Elasticsearch connection
- Enable DEBUG logging for Spring Data Elasticsearch

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 4: Create Product Entity

**Files:**
- Create: `lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/entity/Product.java`

- [ ] **Step 1: Create Product entity with Elasticsearch annotations**

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

- [ ] **Step 2: Verify compilation**

Run: `cd lin-spring-elasticsearch && ./mvnw clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/entity/
git commit -m "feat: add Product entity

- Document with @Document annotation
- Text fields for name/description (full-text search)
- Keyword fields for category/tags (exact match)
- Numeric field for price
- Date field for createdAt

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 5: Create Product Repository

**Files:**
- Create: `lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/repository/ProductRepository.java`

- [ ] **Step 1: Create ProductRepository interface**

```java
package com.lin.spring.elasticsearch.repository;

import com.lin.spring.elasticsearch.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
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

- [ ] **Step 2: Verify compilation**

Run: `cd lin-spring-elasticsearch && ./mvnw clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/repository/
git commit -m "feat: add ProductRepository interface

- Extend ElasticsearchRepository for CRUD
- Query methods for basic searches
- Multi-field search with pagination
- Advanced filtered search methods

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 6: Create ProductService

**Files:**
- Create: `lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/service/ProductService.java`

- [ ] **Step 1: Create ProductService class**

```java
package com.lin.spring.elasticsearch.service;

import com.lin.spring.elasticsearch.entity.Product;
import com.lin.spring.elasticsearch.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    @Autowired
    private ProductRepository repository;

    public Product create(Product product) {
        product.setCreatedAt(LocalDateTime.now());
        return repository.save(product);
    }

    public Optional<Product> getById(String id) {
        return repository.findById(id);
    }

    public List<Product> getAll() {
        return repository.findAll();
    }

    public Product update(String id, Product product) {
        product.setId(id);
        Product existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        product.setCreatedAt(existing.getCreatedAt());
        return repository.save(product);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    public Page<Product> search(String keyword, Pageable pageable) {
        return repository.findByNameContainingOrDescriptionContaining(
                keyword, keyword, pageable);
    }

    public Page<Product> advancedSearch(String category, Double minPrice, Double maxPrice, Pageable pageable) {
        if (category != null && maxPrice != null) {
            return repository.findByCategoryAndPriceLessThanEqual(category, maxPrice, pageable);
        } else if (category != null) {
            return repository.findByCategory(category, pageable);
        } else if (minPrice != null && maxPrice != null) {
            return repository.findByPriceBetween(minPrice, maxPrice, pageable);
        }
        return repository.findAll(pageable);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd lin-spring-elasticsearch && ./mvnw clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/service/
git commit -m "feat: add ProductService

- CRUD operations with createdAt handling
- Full-text search across name and description
- Advanced search with filters (category, price range)
- Proper error handling for missing products

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 7: Create ProductController

**Files:**
- Create: `lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/controller/ProductController.java`

- [ ] **Step 1: Create ProductController with REST endpoints**

```java
package com.lin.spring.elasticsearch.controller;

import com.lin.spring.elasticsearch.entity.Product;
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
    public ResponseEntity<Product> getById(@PathVariable String id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Product>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@PathVariable String id, @RequestBody Product product) {
        try {
            return ResponseEntity.ok(service.update(id, product));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<Page<Product>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name"));
        return ResponseEntity.ok(service.search(keyword, pageable));
    }

    @GetMapping("/advanced")
    public ResponseEntity<Page<Product>> advancedSearch(
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

Run: `cd lin-spring-elasticsearch && ./mvnw clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/controller/
git commit -m "feat: add ProductController

- POST /api/products - Create product
- GET /api/products/{id} - Get by ID
- GET /api/products - Get all products
- PUT /api/products/{id} - Update product
- DELETE /api/products/{id} - Delete product
- GET /api/products/search - Full-text search
- GET /api/products/advanced - Advanced filtered search

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 8: Create Main Application Class

**Files:**
- Create: `lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/LinSpringElasticsearchApplication.java`

- [ ] **Step 1: Create main application class**

```java
package com.lin.spring.elasticsearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LinSpringElasticsearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinSpringElasticsearchApplication.class, args);
    }
}
```

- [ ] **Step 2: Verify application builds**

Run: `cd lin-spring-elasticsearch && ./mvnw clean package`
Expected: BUILD SUCCESS with JAR created

- [ ] **Step 3: Commit**

```bash
git add lin-spring-elasticsearch/src/main/java/com/lin/spring/elasticsearch/LinSpringElasticsearchApplication.java
git commit -m "feat: add main application class

- Spring Boot main class with @SpringBootApplication
- Ready for Elasticsearch integration

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 9: Create API Test File

**Files:**
- Create: `lin-spring-elasticsearch/src/test/resources/api.http`

- [ ] **Step 1: Create api.http with test requests**

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

### Create Laptop Product
POST http://localhost:8080/api/products
Content-Type: application/json

{
  "name": "Gaming Laptop",
  "description": "Powerful laptop for gaming and content creation",
  "price": 1299.99,
  "category": "Electronics",
  "tags": ["computer", "gaming", "performance"]
}

### Get All Products
GET http://localhost:8080/api/products

### Get Product by ID
GET http://localhost:8080/api/products/{{id}}

### Update Product
PUT http://localhost:8080/api/products/{{id}}
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

### Search Products (electronics)
GET http://localhost:8080/api/products/search?keyword=electronics&page=0&size=10

### Advanced Search (Electronics under $200)
GET http://localhost:8080/api/products/advanced?category=Electronics&maxPrice=200&page=0&size=10

### Advanced Search (Price range)
GET http://localhost:8080/api/products/advanced?minPrice=50&maxPrice=150&page=0&size=10

### Delete Product
DELETE http://localhost:8080/api/products/{{id}}
```

- [ ] **Step 2: Commit**

```bash
git add lin-spring-elasticsearch/src/test/resources/api.http
git commit -m "feat: add API test requests

- Sample requests for all endpoints
- Multiple product creation examples
- Search and advanced search examples
- CRUD operation examples

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 10: Create Sample Data File

**Files:**
- Create: `lin-spring-elasticsearch/src/main/resources/data.json`

- [ ] **Step 1: Create sample data JSON**

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
git commit -m "feat: add sample product data

- 5 sample products for testing
- Mix of Electronics and Sports categories
- Various price points for range queries

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

### Task 11: End-to-End Testing

**Files:**
- Test: `lin-spring-elasticsearch/` (entire module)

- [ ] **Step 1: Start Elasticsearch**

Run: `cd lin-spring-elasticsearch && docker-compose up -d`
Expected: Container started, ES accessible at http://localhost:9200

- [ ] **Step 2: Verify Elasticsearch is running**

Run: `curl http://localhost:9200`
Expected: JSON response with Elasticsearch version info

- [ ] **Step 3: Build and run application**

Run: `cd lin-spring-elasticsearch && ./mvnw spring-boot:run`
Expected: Application starts on port 8080, connects to Elasticsearch

- [ ] **Step 4: Test create product endpoint**

Run: Use POST request from api.http to create a product
Expected: 201 Created with product JSON response including generated ID

- [ ] **Step 5: Test get all products**

Run: Use GET http://localhost:8080/api/products
Expected: 200 OK with array of products

- [ ] **Step 6: Test search endpoint**

Run: Use GET http://localhost:8080/api/products/search?keyword=wireless
Expected: 200 OK with paginated results

- [ ] **Step 7: Test advanced search**

Run: Use GET http://localhost:8080/api/products/advanced?category=Electronics&maxPrice=100
Expected: 200 OK with filtered results

- [ ] **Step 8: Test update endpoint**

Run: Use PUT request to update the created product
Expected: 200 OK with updated product

- [ ] **Step 9: Test delete endpoint**

Run: Use DELETE request with product ID
Expected: 204 No Content

- [ ] **Step 10: Stop Elasticsearch**

Run: `cd lin-spring-elasticsearch && docker-compose down`
Expected: Container stopped and removed

---

### Task 12: Final Documentation

**Files:**
- Create: `lin-spring-elasticsearch/README.md`

- [ ] **Step 1: Create README.md**

```markdown
# lin-spring-elasticsearch

Spring Boot Elasticsearch integration demo demonstrating CRUD operations and full-text search.

## Features

- Document indexing with Spring Data Elasticsearch
- Full-text search across multiple fields
- Advanced search with filters and pagination
- Repository-based query derivation
- Docker Compose for local Elasticsearch

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

3. Test the APIs:
- Use `src/test/resources/api.http` with your HTTP client
- Or import sample data from `src/main/resources/data.json`

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/products` | Create product |
| GET | `/api/products/{id}` | Get by ID |
| GET | `/api/products` | Get all |
| PUT | `/api/products/{id}` | Update product |
| DELETE | `/api/products/{id}` | Delete product |
| GET | `/api/products/search` | Full-text search |
| GET | `/api/products/advanced` | Advanced search |

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
curl "http://localhost:8080/api/products/search?keyword=wireless&page=0&size=10"
```

### Advanced Search
```bash
curl "http://localhost:8080/api/products/advanced?category=Electronics&maxPrice=100"
```

## Shutdown

Stop Elasticsearch:
```bash
docker-compose down
```
```

- [ ] **Step 2: Commit**

```bash
git add lin-spring-elasticsearch/README.md
git commit -m "docs: add README with usage instructions

- Quick start guide
- API endpoints reference
- Example usage with curl
- Setup and shutdown instructions

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Verification Checklist

After completing all tasks:

- [ ] Module builds successfully with `./mvnw clean package`
- [ ] Elasticsearch starts with `docker-compose up -d`
- [ ] Application starts without errors
- [ ] All CRUD operations work via api.http
- [ ] Full-text search returns relevant results
- [ ] Advanced search with filters works
- [ ] Pagination functions correctly
- [ ] Code follows existing project patterns (CLAUDE.md)
