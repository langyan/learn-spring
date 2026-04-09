# 设计分布式系统中的幂等性

这个示例项目把幂等性拆成两层来演示：

- API 请求幂等：同一个 `Idempotency-Key` 重复提交，只创建一条订单。
- Kafka 消费幂等：同一个 `eventId` 被重复投递时，下游奖励逻辑只执行一次。

## 目录

- 后端：`lin-spring-idempotency/lin-spring-idempotency-backend`
- 前端：`lin-spring-idempotency/lin-react-idempotency-demo`
- 容器编排：`lin-spring-idempotency/docker-compose.yml`

## 后端设计

### API 幂等

`POST /api/orders` 要求带 `Idempotency-Key` 请求头。

后端把请求摘要和处理状态写入 Redis：

- Redis Key：`idempotency:order:{idempotencyKey}`
- 状态：`PROCESSING`、`COMPLETED`
- 内容：请求摘要、订单号、首次成功响应

处理流程：

1. 首次请求先在 Redis 中抢占 `PROCESSING` 状态。
2. 订单创建成功后，写入 `COMPLETED` 和响应快照。
3. 同一个 key 再次提交：
   - 请求体相同：直接回放缓存响应。
   - 请求体不同：返回 409 冲突。

### Kafka 消费幂等

订单创建后会发送 `order.created` 事件，消费者在处理前先检查 Redis：

- Redis Key：`processed:event:{eventId}`

如果同一个事件再次到达：

- 已处理：直接 `ack`
- 未处理：先抢占处理资格，再写入奖励记录

这个版本是教学型实现，重点展示“去重键 + 手动 ack + 重放验证”的思路。

## 主要接口

- `POST /api/orders`
- `GET /api/orders/{orderNo}`
- `POST /api/orders/{orderNo}/events/replay`
- `GET /api/orders/{orderNo}/reward-records`

`api.http` 已经提供了最基本的接口调试脚本。

## 本地运行

### 方式一：分别启动

后端：

```bash
cd lin-spring-idempotency/lin-spring-idempotency-backend
./mvnw spring-boot:run
```

前端：

```bash
cd lin-spring-idempotency/lin-react-idempotency-demo
npm install
npm run dev
```

依赖服务可以单独启动 Redis 和 Kafka，或者直接使用下面的 Docker Compose。

### 方式二：Docker Compose

在 `lin-spring-idempotency` 目录执行：

```bash
cd lin-spring-idempotency
docker compose up --build
```

启动后访问：

- 前端：[http://localhost:5173](http://localhost:5173)
- 后端：[http://localhost:8086](http://localhost:8086)
- H2 Console：[http://localhost:8086/h2-console](http://localhost:8086/h2-console)

H2 连接信息：

- JDBC URL：`jdbc:h2:mem:idempotencydb`
- User Name：`sa`
- Password：留空

## 验证步骤

### 验证 API 请求幂等

1. 打开前端页面。
2. 保持同一个 `Idempotency-Key`，点击“用同一 Key 连续提交两次”。
3. 观察返回结果：订单号相同，第二次响应会显示命中幂等缓存。

### 验证幂等键冲突

1. 先用一个 `Idempotency-Key` 创建订单。
2. 保持同一个 key，但修改商品名或数量。
3. 再次提交后应返回 409。

### 验证 Kafka 消费幂等

1. 创建订单后，页面会展示奖励记录。
2. 点击“重放同一个 Kafka 事件”。
3. 重新查询奖励记录，记录数应保持不变。

## 技术栈

- React
- Spring Boot
- Redis
- Kafka
- Docker
- H2
