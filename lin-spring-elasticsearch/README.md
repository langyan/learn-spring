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
