# Lin-Spring-Microservices

A comprehensive Spring Boot microservices architecture demonstrating the 10 best practices for building production-ready microservices.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        API Gateway (8080)                       │
│              (Spring Cloud Gateway + Security)                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Service Discovery (8761)                    │
│                         (Eureka Server)                         │
└─────────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ Config Server│    │              │    │              │
│    (8888)    │    │              │    │              │
└──────────────┘    │              │    │              │
                    │  Services    │    │              │
         ┌──────────┴──────────┬───┴──────────────────┤
         ▼                     ▼                      ▼
   ┌──────────┐         ┌──────────┐           ┌──────────┐
   │   Order  │         │ Payment  │           │ Inventory│
   │  (8081)  │◄────────►│ (8082)   │           │  (8083)  │
   └──────────┘         └──────────┘           └──────────┘
         │                     │
         ▼                     ▼
   ┌──────────┐         ┌──────────┐
   │ Shipping │         │   User   │
   │  (8084)  │         │  (8085)  │
   └──────────┘         └──────────┘
```

## Microservices

| Service | Port | Description |
|---------|------|-------------|
| **Eureka Server** | 8761 | Service Discovery Registry |
| **Config Server** | 8888 | Centralized Configuration Management |
| **API Gateway** | 8080 | Single entry point with JWT auth |
| **User Service** | 8085 | User management and JWT authentication |
| **Order Service** | 8081 | Order orchestration with Saga pattern |
| **Payment Service** | 8082 | Payment processing with circuit breaker |
| **Inventory Service** | 8083 | Stock management and reservation |
| **Shipping Service** | 8084 | Shipping and tracking |

## Features Implemented

### 1. Business Capability-Driven Design
Each service represents a specific business domain:
- Order Management
- Payment Processing
- Inventory Management
- Shipping Management
- User Management

### 2. API Gateway
- Single entry point for all client requests
- JWT authentication filter
- Circuit breaker for fault tolerance
- Request routing to microservices

### 3. Service Discovery (Eureka)
- Dynamic service registration
- Service discovery without hardcoded locations
- Health checks for all services

### 4. Centralized Configuration (Spring Cloud Config)
- Shared configuration storage
- Environment-specific configs
- Runtime configuration updates

### 5. Circuit Breakers (Resilience4j)
- Fault tolerance for service failures
- Fallback methods for degraded operations
- Configurable thresholds and timeouts

### 6. Security (JWT)
- Token-based authentication
- Centralized auth at Gateway
- Role-based authorization

### 7. Observability
- Spring Boot Actuator health checks
- Metrics endpoints
- Distributed tracing ready

### 8. Containerization (Docker)
- Dockerfiles for all services
- Docker Compose for orchestration
- Health checks and dependencies

### 9. CI/CD Pipeline (GitHub Actions)
- Automated builds
- Unit tests
- Integration tests
- Docker image builds

## Maven Multi-Module Build

This project uses a parent POM structure for centralized dependency management and build configuration.

### Parent POM Benefits
- **Centralized Dependency Management**: All versions defined in one place
- **Shared Plugins**: Common build plugins configured at parent level
- **Inherited Dependencies**: Lombok, DevTools, Test dependencies inherited by all modules
- **Consistent Build**: Enforce Java 21 and Maven version requirements

### Build All Modules from Root

```bash
# Build all modules
./mvnw clean install

# Build all modules (skip tests)
./mvnw clean install -DskipTests

# Build specific module
./mvnw clean install -pl lin-spring-service-user -am

# Run tests for all modules
./mvnw test

# Package all modules
./mvnw package
```

### Build Individual Modules

```bash
# Build a specific module
cd lin-spring-service-user
./mvnw clean install

# Run a specific service
cd lin-spring-service-user
./mvnw spring-boot:run
```

## Quick Start

### Option 1: Run Individual Services (Development)

```bash
# Terminal 1 - Start Eureka Server
cd lin-spring-cloud-eureka
./mvnw spring-boot:run

# Terminal 2 - Start Config Server
cd lin-spring-cloud-config
./mvnw spring-boot:run

# Terminal 3 - Start User Service
cd lin-spring-service-user
./mvnw spring-boot:run

# Terminal 4 - Start Payment Service
cd lin-spring-service-payment
./mvnw spring-boot:run

# Terminal 5 - Start Inventory Service
cd lin-spring-service-inventory
./mvnw spring-boot:run

# Terminal 6 - Start Shipping Service
cd lin-spring-service-shipping
./mvnw spring-boot:run

# Terminal 7 - Start Order Service
cd lin-spring-service-order
./mvnw spring-boot:run

# Terminal 8 - Start API Gateway
cd lin-spring-cloud-gateway
./mvnw spring-boot:run
```

### Option 2: Docker Compose

```bash
# Build and start all services
cd docker
docker-compose up

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

## API Endpoints

### Through API Gateway (Port 8080)

**Authentication (Public):**
```http
POST /api/auth/register
POST /api/auth/login
```

**User Management (Requires JWT):**
```http
GET /api/users/me
GET /api/users/{id}
```

**Orders (Requires JWT):**
```http
POST /api/orders
GET /api/orders/{id}
GET /api/orders/number/{orderNumber}
```

**Payments (Requires JWT):**
```http
GET /api/payments/{id}
GET /api/payments/order/{orderId}
```

**Inventory (Requires JWT):**
```http
GET /api/inventory
POST /api/inventory/reserve
```

**Shipping (Requires JWT):**
```http
GET /api/shipping/{id}
GET /api/shipping/tracking/{trackingNumber}
```

## Testing the System

### 1. Create User and Get JWT Token

```http
POST http://localhost:8080/api/auth/register
Content-Type: application/json

{
  "username": "john_doe",
  "password": "password123",
  "email": "john@example.com",
  "fullName": "John Doe"
}
```

### 2. Login to Get Token

```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "username": "john_doe",
  "password": "password123"
}
```

### 3. Create Order (with JWT token)

```http
POST http://localhost:8080/api/orders
Authorization: Bearer YOUR_JWT_TOKEN
Content-Type: application/json

{
  "userId": 1,
  "items": [
    {
      "productCode": "PROD-001",
      "productName": "Laptop",
      "quantity": 1,
      "price": 999.99
    }
  ]
}
```

## Service Dashboards

- **Eureka Dashboard**: http://localhost:8761 (admin/password)
- **Config Server**: http://localhost:8888
- **Gateway Routes**: http://localhost:8080/actuator/gateway/routes

## Circuit Breaker Testing

To test circuit breakers:

1. Start all services
2. Create an order successfully
3. Stop one service (e.g., Payment Service)
4. Create another order - fallback will be triggered
5. Restart the service
6. Circuit breaker will recover

## Configuration

All services can be configured via:
1. Local `application.yml`
2. Config Server (http://localhost:8888)
3. Environment variables

## Technologies Used

- **Spring Boot 3.5.3**
- **Spring Cloud 2024.0.0**
- **Spring Cloud Gateway**
- **Netflix Eureka**
- **Spring Cloud Config**
- **Resilience4j**
- **JWT (io.jsonwebtoken)**
- **H2 Database**
- **Docker & Docker Compose**
- **GitHub Actions**

## Best Practices Demonstrated

1. ✅ Design around business capabilities
2. ✅ Keep services small and focused
3. ✅ Implement API Gateway
4. ✅ Use Service Discovery
5. ✅ Implement Circuit Breakers
6. ✅ Strong observability with Actuator
7. ✅ Centralized configuration management
8. ✅ JWT authentication and authorization
9. ✅ Docker containerization
10. ✅ CI/CD automation

## Project Structure

```
lin-spring-microservices/
├── pom.xml                          # Parent POM (dependency management)
├── lin-spring-cloud-gateway/         # API Gateway
│   └── pom.xml                      # Inherits from parent
├── lin-spring-cloud-eureka/          # Service Discovery
│   └── pom.xml                      # Inherits from parent
├── lin-spring-cloud-config/          # Config Server
│   └── pom.xml                      # Inherits from parent
├── lin-spring-service-order/         # Order Service
│   └── pom.xml                      # Inherits from parent
├── lin-spring-service-payment/       # Payment Service
│   └── pom.xml                      # Inherits from parent
├── lin-spring-service-inventory/     # Inventory Service
│   └── pom.xml                      # Inherits from parent
├── lin-spring-service-shipping/      # Shipping Service
│   └── pom.xml                      # Inherits from parent
├── lin-spring-service-user/          # User Service
│   └── pom.xml                      # Inherits from parent
├── docker/                           # Docker Compose
│   └── docker-compose.yml
└── .github/workflows/                # CI/CD Pipeline
    └── microservices-ci.yml
```

## License

This project is for learning purposes.
