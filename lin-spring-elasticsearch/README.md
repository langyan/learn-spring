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
