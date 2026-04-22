# lin-spring-outbox

演示 **Transactional Outbox（事务性发件箱）**：订单与待投递事件在 **同一本地数据库事务** 中写入，避免「库已提交、消息未发出」的双写不一致；提交后由定时任务轮询 Outbox，再调用可插拔的 `OutboxMessagePublisher` 投递（默认写日志 + 内存副本，可选 Kafka）。

## 前置条件

- JDK 21
- Maven 3.9+
- 使用 Kafka 模式时：Docker（用于 `docker-compose.yml`）

## 快速启动（默认，无 Kafka）

```bash
cd lin-spring-outbox
mvn spring-boot:run
```

默认使用 H2 内存库与 `LoggingOutboxMessagePublisher`，无需额外中间件。

## 使用 Docker 中的 Kafka

1. 在本模块目录启动 Kafka 与 Zookeeper：

   ```bash
   docker compose up -d
   ```

   宿主机访问 Broker：`127.0.0.1:9092`。Zookeeper 映射到宿主机 **2182**（避免与常见 2181 冲突）。

2. 使用 `kafka` profile 启动应用：

   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=kafka
   ```

   Broker 地址可通过环境变量 `KAFKA_BOOTSTRAP_SERVERS` 覆盖；消息写入主题由 `app.outbox.kafka.topic` 配置（默认 `order-events`），见 `src/main/resources/application-kafka.properties`。

## HTTP 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/orders` | 创建订单（同事务写入 Outbox） |
| GET | `/api/orders/{id}` | 查询订单 |
| GET | `/api/outbox/pending` | 列出待投递的 Outbox 记录（教学用） |

请求体示例见仓库内 `api.http`。

## 构建与测试

```bash
mvn clean test
mvn clean package
```

## 配置要点

- 轮询间隔、批量大小、最大重试：`app.outbox.relay.*`（`application.properties`）
- 与「提交后再发 Kafka、失败才写库」的补救式写法不同，本模块在 `OrderService#createOrder` 中 **先落库订单与 Outbox 再提交事务**，由 `OutboxRelayScheduler` 异步中继。

## 参考

