# Spring Cloud Saga 分布式事务项目

这是一个基于 Spring Cloud 的 Saga 模式分布式事务实现，演示了订单创建、支付处理、库存预留的完整业务流程。

## 项目架构

```
lin-spring-cloud-saga (父 POM)
├── lin-spring-cloud-saga-common (公共模块)
├── lin-spring-cloud-saga-eureka-server (服务注册中心，端口 8761)
├── lin-spring-cloud-saga-orchestrator (Saga编排器，端口 8080)
├── lin-spring-cloud-saga-order-service (订单服务，端口 8081)
├── lin-spring-cloud-saga-payment-service (支付服务，端口 8082)
└── lin-spring-cloud-saga-inventory-service (库存服务，端口 8083)
```

## 技术栈

- Spring Boot 3.5.0
- Spring Cloud 2025.0.0
- Java 21
- Maven
- H2 数据库（内存数据库）
- Lombok
- OpenFeign
- Eureka 服务注册中心
- Spring Retry

## Saga 流程

### 正常流程
1. 创建订单 → 2. 处理支付 → 3. 预留库存 → 4. 完成订单

### 补偿流程（当任何步骤失败时）
1. 释放库存 → 2. 退款 → 3. 取消订单

## 启动顺序

1. 启动 Eureka 服务注册中心
2. 启动订单服务
3. 启动支付服务
4. 启动库存服务
5. 启动 Saga 编排器

## API 文档

### Saga 编排器 API

#### 执行 Saga 流程
```http
POST http://localhost:8080/api/saga/execute
Content-Type: application/json

{
  "userId": "user123",
  "productId": "product456",
  "quantity": 2,
  "amount": 99.99
}
```

#### 查询 Saga 状态
```http
GET http://localhost:8080/api/saga/{sagaId}
```

### 订单服务 API

#### 创建订单
```http
POST http://localhost:8081/api/orders
Content-Type: application/json

{
  "userId": "user123",
  "productId": "product456",
  "quantity": 2,
  "amount": 99.99
}
```

#### 获取订单
```http
GET http://localhost:8081/api/orders/{orderId}
```

### 支付服务 API

#### 处理支付
```http
POST http://localhost:8082/api/payments
Content-Type: application/json

{
  "orderId": "ORD_123456",
  "userId": "user123",
  "amount": 99.99
}
```

#### 退款
```http
POST http://localhost:8082/api/payments/{paymentId}/refund
```

### 库存服务 API

#### 预留库存
```http
POST http://localhost:8083/api/inventory/reserve
Content-Type: application/json

{
  "orderId": "ORD_123456",
  "productId": "product456",
  "quantity": 2
}
```

#### 释放库存
```http
POST http://localhost:8083/api/inventory/{inventoryId}/release
```

## 测试用例

### 成功场景
```bash
# 执行 Saga 流程
curl -X POST http://localhost:8080/api/saga/execute \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user123",
    "productId": "product456",
    "quantity": 2,
    "amount": 99.99
  }'
```

### 失败场景
- 支付金额超过 1000 元会失败
- 支付有 10% 的随机失败概率
- 库存预留有 5% 的随机失败概率

## 数据库管理

每个服务都集成了 H2 控制台，可以通过以下 URL 访问：

- Eureka: http://localhost:8761 (admin/password)
- 订单服务: http://localhost:8081/h2-console
- 支付服务: http://localhost:8082/h2-console
- 库存服务: http://localhost:8083/h2-console
- Saga编排器: http://localhost:8080/h2-console

## 构建和运行

### 构建所有模块
```bash
mvn clean compile
```

### 运行单个服务
```bash
cd lin-spring-cloud-saga-eureka-server
mvn spring-boot:run
```

### 运行所有服务
```bash
# 在父项目目录下
mvn spring-boot:run -pl lin-spring-cloud-saga-eureka-server &
mvn spring-boot:run -pl lin-spring-cloud-saga-order-service &
mvn spring-boot:run -pl lin-spring-cloud-saga-payment-service &
mvn spring-boot:run -pl lin-spring-cloud-saga-inventory-service &
mvn spring-boot:run -pl lin-spring-cloud-saga-orchestrator
```

## 特性

1. **分布式事务管理**: 使用 Saga 模式管理跨服务的业务事务
2. **服务发现**: 通过 Eureka 实现服务注册与发现
3. **重试机制**: 使用 Spring Retry 实现失败重试
4. **补偿事务**: 完整的补偿机制确保数据一致性
5. **状态跟踪**: 实时跟踪 Saga 执行状态
6. **错误处理**: 完善的异常处理和错误恢复机制

## 监控和管理

每个服务都集成了 Spring Boot Actuator，可以通过以下端点监控服务状态：

- `/actuator/health` - 健康检查
- `/actuator/info` - 应用信息
- `/actuator/metrics` - 应用指标