# lin-spring-outbox

演示 **Transactional Outbox（事务性发件箱）**：在 **同一本地数据库事务** 中写入业务数据（订单）与待投递事件（`outbox_messages`），避免「数据库已提交、下游消息未发出」的经典双写不一致；事务提交后由 **定时轮询** 调用可插拔的 `OutboxMessagePublisher` 完成投递。

## 解决的问题

| 朴素做法 | 风险 |
|----------|------|
| 先写库再发 Kafka | 进程在两者之间崩溃 → 消息永久丢失 |
| 先发 Kafka 再写库 | 库写失败 → 下游已消费，数据不一致 |

Outbox 将「业务落库」与「事件落库」绑定在同一 `@Transactional` 边界内，要么一起成功，要么一起回滚；消息系统由中继进程 **异步、可重试** 地读取 Outbox 表再发送。

## 运行时数据流

1. `POST /api/orders` → `OrderService#createOrder` 保存 `Order` 与 `OutboxMessage`（状态 `PENDING`），一次提交。
2. `OutboxRelayScheduler` 按固定延迟调用 `OutboxRelayService#processPendingBatch`。
3. 对每条记录：`OutboxMessagePublisher#publish`；成功则标记 `PUBLISHED`，失败则增加重试次数，超过 `app.outbox.relay.max-retries` 则标记 `FAILED`。

单条处理在独立事务中（`processOne`），避免一条失败拖垮整批已更新的状态。

## 技术栈

- Spring Boot **3.4.4**、Java **21**
- Spring Data JPA、H2（默认内存库）
- Spring Scheduling（轮询）
- Spring Kafka（可选，`kafka` profile）

## 前置条件

- JDK 21、Maven 3.9+
- 使用 Kafka 模式时：Docker（用于本模块 `docker-compose.yml`）

## 快速启动（默认，无 Kafka）

```bash
cd lin-spring-outbox
mvn spring-boot:run
```

默认激活 **非** `kafka` profile：使用 `LoggingOutboxMessagePublisher`（打日志 + 内存中的已投递副本，便于本地与测试断言），无需 Kafka。

## 使用 Docker 中的 Kafka

1. 在本模块目录启动 Zookeeper 与 Kafka：

   ```bash
   docker compose up -d
   ```

   - 宿主机访问 Broker：`127.0.0.1:9092`
   - Zookeeper 映射到宿主机 **2182**（避免与常见 2181 冲突）

2. 使用 `kafka` profile 启动应用：

   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=kafka
   ```

3. 配置说明（见 `src/main/resources/application-kafka.properties`）：

   - `spring.kafka.bootstrap-servers`：默认 `127.0.0.1:9092`，可通过环境变量 **`KAFKA_BOOTSTRAP_SERVERS`** 覆盖。
   - `app.outbox.kafka.topic`：默认 `order-events`。
   - `app.outbox.kafka.send-timeout-seconds`：发送等待超时（默认 5，定义在 `KafkaOutboxMessagePublisher`）。

主类排除了 `KafkaAutoConfiguration`，仅在 `kafka` profile 下由 `KafkaOutboxConfiguration` 显式注册生产者，避免无 Broker 时默认自动配置启动失败。

## HTTP 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/orders` | 创建订单（同事务写入 Outbox） |
| GET | `/api/orders/{id}` | 查询订单 |
| GET | `/api/outbox/pending` | 列出待投递的 Outbox 记录（教学/排障用） |

请求示例见仓库内 `api.http`。

## 配置要点

| 配置项 | 说明 | 默认（`application.properties`） |
|--------|------|-----------------------------------|
| `app.outbox.relay.fixed-delay-ms` | 轮询间隔（毫秒） | `2000` |
| `app.outbox.relay.batch-size` | 每批最多处理条数 | `20` |
| `app.outbox.relay.max-retries` | 单条最大重试次数，超出标记 `FAILED` | `3` |

数据库与 JPA 演示配置见 `application.properties`（如 `spring.jpa.hibernate.ddl-auto=create-drop`）。**生产环境**应使用受控的 schema 迁移（Flyway/Liquibase 等）与合适的 DDL 策略，而非依赖 `create-drop`。

## 代码结构（主要包）

| 包/类 | 职责 |
|--------|------|
| `service.OrderService` | 创建订单并写入 Outbox（同一事务） |
| `service.OutboxRelayService` | 批处理待投递记录、更新状态与重试 |
| `job.OutboxRelayScheduler` | `@Scheduled` 触发中继 |
| `outbox.OutboxMessagePublisher` | 投递抽象 |
| `outbox.LoggingOutboxMessagePublisher` | 默认实现（`!kafka`） |
| `outbox.KafkaOutboxMessagePublisher` | Kafka 实现（`kafka` profile） |
| `entity.OutboxMessage` / `OutboxStatus` | Outbox 表与状态枚举 |

## 构建与测试

```bash
mvn clean test
mvn clean package
```

集成测试见 `OrderOutboxIntegrationTest`、`OutboxRelayServiceTest` 等。

## 走向生产时的简要提醒

- **至少一次投递**：消费者侧应对 `eventId` 等做 **幂等** 去重。
- **轮询延迟与吞吐**：根据业务量调整 `fixed-delay-ms` 与 `batch-size`；高负载可考虑 Debezium 等 CDC 驱动 Outbox，替代纯轮询。
- **密钥与连接串**：勿将密码、SASL 密钥写入仓库；通过环境变量或外部配置注入（本模块 Kafka 示例已用 `KAFKA_BOOTSTRAP_SERVERS` 说明方向）。

## 参考

- [Transactional Outbox](https://microservices.io/patterns/data/transactional-outbox.html)（microservices.io）
- [Implementing the Outbox Pattern](https://debezium.io/blog/2018/07/19/reliable-microservices-data-exchange-with-the-outbox-pattern/)（Debezium 博客）
