# lin-spring-elasticsearch

Spring Boot demo implementing CQRS pattern with event-driven architecture and Outbox pattern for dual-storage consistency between MySQL and Elasticsearch.

## Architecture

```
┌─────────────┐
│   Controller│
└──────┬──────┘
       │
       ├──────────────┬──────────────┬──────────────┐
       │              │              │              │
       ▼              ▼              ▼              ▼
┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────────┐
│Write Svc │  │Read Svc  │  │SearchSvc │  │Outbox Ctrl │
└─────┬────┘  └─────┬────┘  └─────┬────┘  └────────────┘
      │             │             │
      ▼             ▼             ▼
┌─────────┐  ┌─────────┐  ┌──────────────┐
│Outbox TB│  │MySQL DB │  │Elasticsearch │
└────┬────┘  └─────────┘  └──────────────┘
     │
     ▼
┌──────────────┐
│Event Poller  │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│Kafka Producer│
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ Kafka Topic  │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│Event Consumer│──► Update Elasticsearch
└──────────────┘
```

### Key Patterns

1. **CQRS (Command Query Responsibility Segregation)**
   - Write operations: `ProductWriteService` (MySQL + Outbox)
   - Read operations: `ProductReadService` (MySQL)
   - Search operations: `ProductSearchService` (Elasticsearch)

2. **Event-Driven Architecture**
   - Domain events: `ProductEvent` (CREATED, UPDATED, DELETED)
   - Kafka for async event delivery
   - Event consumer updates Elasticsearch index

3. **Outbox Pattern**
   - Transactional outbox table ensures DB + event consistency
   - Background poller publishes events to Kafka
   - Retry mechanism for failed events
   - Manual cleanup endpoint for completed events

## Tech Stack

- **Spring Boot 3.5.0** (Java 21)
- **Spring Data JPA** - MySQL database access
- **Spring Data Elasticsearch** - Search engine
- **Spring Kafka** - Event streaming
- **Elasticsearch 9.x** with IK Chinese word segmenter
- **Kafka** - Message broker
- **MySQL** - Primary database
- **Lombok** - Reduce boilerplate
- **Jackson** - JSON serialization

## Prerequisites

- Java 21
- Maven
- Docker and Docker Compose

## Quick Start

1. Start infrastructure services (Elasticsearch + Kafka + MySQL):
```bash
docker-compose up -d
```

2. Wait for services to be ready (Elasticsearch may take 30-60 seconds):
```bash
docker-compose logs -f elasticsearch
```

3. Run the application:
```bash
./mvnw spring-boot:run
```

4. Test the APIs using `src/test/resources/api.http`

## API Endpoints

### Product Management (CQRS)

| Method | Path | Description | Service |
|--------|------|-------------|---------|
| POST | `/api/products` | Create product | WriteService |
| PUT | `/api/products/{id}` | Update product | WriteService |
| DELETE | `/api/products/{id}` | Delete product | WriteService |
| GET | `/api/products/{id}` | Get by ID | ReadService |
| GET | `/api/products` | Get all | ReadService |

### Search Operations

| Method | Path | Description | Features |
|--------|------|-------------|----------|
| GET | `/api/products/search` | Full-text search | IK Chinese tokenizer |
| POST | `/api/products/advanced` | Advanced search | Category + price range |
| GET | `/api/products/aggregate/category` | Category stats | Aggregation |
| GET | `/api/products/aggregate/price` | Price range stats | Histogram aggregation |
| GET | `/api/products/search-with-aggregations` | Search + aggregates | Combined results |

### Outbox Management

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/outbox/pending` | Get pending events |
| GET | `/api/outbox/failed` | Get failed events |
| POST | `/api/outbox/retry/{id}` | Retry failed event |
| DELETE | `/api/outbox/{id}` | Delete event |
| DELETE | `/api/outbox/cleanup` | Cleanup old events |

## Example Usage

### Create Product (Chinese)
```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "无线降噪耳机",
    "description": "高品质主动降噪蓝牙耳机，支持30小时续航",
    "price": 299.99,
    "category": "电子产品",
    "tags": ["音频", "无线", "蓝牙", "降噪"]
  }'
```

### Full-Text Search (Chinese)
```bash
curl "http://localhost:8080/api/products/search?keyword=耳机"
```

### Advanced Search with Filters
```bash
curl -X POST http://localhost:8080/api/products/advanced \
  -H "Content-Type: application/json" \
  -d '{
    "category": "电子产品",
    "minPrice": 100,
    "maxPrice": 1000
  }'
```

### Search with Aggregations
```bash
curl "http://localhost:8080/api/products/search-with-aggregations?keyword=无线&category=电子产品&minPrice=200&maxPrice=500"
```

### Check Outbox Status
```bash
curl http://localhost:8080/api/outbox/pending
curl http://localhost:8080/api/outbox/failed
```

### Retry Failed Event
```bash
curl -X POST http://localhost:8080/api/outbox/retry/1
```

## Configuration

### application.properties
```properties
# MySQL Database
spring.datasource.url=jdbc:mysql://localhost:3306/productdb
spring.datasource.username=productuser
spring.datasource.password=productpass

# Elasticsearch
spring.elasticsearch.uris=http://localhost:9200

# Kafka
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=product-sync-group

# Outbox Poller
outbox.poller.interval=5000
outbox.poller.batch-size=10
```

## Event Flow

1. **Write Operation**: User creates/updates/deletes product via API
2. **Transactional Outbox**: Service saves to MySQL and creates outbox event in same transaction
3. **Event Polling**: Background task polls pending outbox events
4. **Kafka Publishing**: Events published to Kafka topic
5. **Event Consumption**: Kafka consumer receives event
6. **Elasticsearch Update**: Consumer updates/ deletes document in Elasticsearch index
7. **Status Update**: Outbox event marked as completed

## Database Schema

### products (MySQL)
```sql
CREATE TABLE products (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255),
  description VARCHAR(1000),
  price DECIMAL(10,2),
  category VARCHAR(100),
  tags JSON,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);
```

### outbox (MySQL)
```sql
CREATE TABLE outbox (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  aggregate_type VARCHAR(100),
  aggregate_id BIGINT,
  event_type VARCHAR(50),
  payload JSON,
  status VARCHAR(20),
  retry_count INT DEFAULT 0,
  created_at TIMESTAMP,
  next_retry_at TIMESTAMP,
  completed_at TIMESTAMP
);
```

## Elasticsearch Index Settings

The products index uses IK analyzer for Chinese text segmentation:
- **Analyzer**: `ik_max_word` (indexing), `ik_smart` (searching)
- **Fields**: name, description (full-text search)
- **Aggregations**: category (terms), price (histogram)

## Monitoring

- **Outbox Table**: Monitor pending/failed events
- **Kafka Logs**: Check event delivery status
- **Elasticsearch**: Verify index updates
- **Application Logs**: Track sync process

## Shutdown

Stop infrastructure services:
```bash
docker-compose down -v
```

**Note**: MySQL data is persisted in Docker volumes. Add `-v` to remove data.

## Troubleshooting

1. **Elasticsearch connection failed**: Ensure Elasticsearch is ready (wait 30-60s after `docker-compose up`)
2. **Kafka connection errors**: Check Kafka container status
3. **Outbox events pending**: Verify Kafka consumer is running
4. **Search returns empty**: Check if events were processed (check outbox table)

## Future Improvements

- Add retry backoff strategy for failed events
- Implement event schema evolution
- Add metrics/monitoring (Prometheus, Grafana)
- Implement distributed tracing
- Add batch indexing for bulk operations
