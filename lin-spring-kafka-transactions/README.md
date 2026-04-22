# lin-spring-kafka-transactions

演示 **Kafka 生产者事务**与 **`read_committed`** 消费者，两种写法并列：

| 方式 | Service | 说明 |
|------|-----------|------|
| 编程式 | `TransactionalDemoService` | `KafkaTemplate.executeInTransaction(...)` |
| 声明式 | `TransactionalDeclarativeDemoService` | `@Transactional(transactionManager = "kafkaTransactionManager")` + `send` / `flush` |
| 链式库+Kafka | `DbAndKafkaChainedDemoService` | `@Transactional(transactionManager = "chainedTransactionManager")`，底层为 `ChainedTransactionManager`（JPA + Kafka） |

同一事务内向 `demo-tx-a`、`demo-tx-b` 发两条：要么提交后均被消费者看到，要么异常中止且均不可见。声明式示例的 value 后缀为 `|c` / `|d`，编程式为 `|a` / `|b`，便于区分。

**库 + Kafka**：`DbAndKafkaChainedDemoService` 写入 H2 表 `kafka_tx_demo_record` 并向 `demo-tx-db` 发消息（value 后缀 `|db`）。本模块显式注册 `transactionManager`（`JpaTransactionManager`），否则 Spring Data JPA 在部分 Boot 版本下无法解析默认事务管理器。链式事务**不是 2PC**，提交顺序上仍可能出现一端成功、一端失败的窗口；生产上更稳妥的「写库与发消息」模型见 **`lin-spring-outbox`**（Transactional Outbox）。

参考阅读（正文为会员内容，抓取工具仅能访问引言）：[Understanding Apache Kafka Transactions (Medium)](https://medium.com/codefarm-java-ecosystem/understanding-apache-kafka-transactions-guarantees-boundaries-and-a-practical-spring-boot-a0a0dda8f765)

## 自动验证（推荐）

无需本机 Docker，嵌入式 Broker 跑集成测试：

```bash
cd lin-spring-kafka-transactions
mvn test
```

## 本地手动验证

1. 启动 Kafka（与 `application.yml` 中 `localhost:9092` 一致）：

   ```bash
   docker compose up -d
   ```

2. 在 Broker 上创建 Topic（若尚未存在）：

   ```bash
   docker exec lin-spring-kafka-tx-kafka kafka-topics --bootstrap-server localhost:9092 --create --if-not-exists --topic demo-tx-a --partitions 1 --replication-factor 1
   docker exec lin-spring-kafka-tx-kafka kafka-topics --bootstrap-server localhost:9092 --create --if-not-exists --topic demo-tx-b --partitions 1 --replication-factor 1
   docker exec lin-spring-kafka-tx-kafka kafka-topics --bootstrap-server localhost:9092 --create --if-not-exists --topic demo-tx-db --partitions 1 --replication-factor 1
   ```

3. 启动应用（默认端口 `8095`）：

   ```bash
   mvn spring-boot:run
   ```

4. 使用 `api.http` 或 curl 调用：

   - `POST /api/tx/commit/{correlationId}` — 编程式提交。
   - `POST /api/tx/rollback/{correlationId}` — 编程式中止。
   - `POST /api/tx/declarative/commit/{correlationId}` — 声明式提交（`|c` / `|d`）。
   - `POST /api/tx/declarative/rollback/{correlationId}` — 声明式中止。
   - `POST /api/tx/db-kafka/commit/{correlationId}` — 链式：写 H2 + 发 `demo-tx-db`。
   - `POST /api/tx/db-kafka/rollback/{correlationId}` — 链式：写库并发 Kafka 后失败，库与 Kafka 均应回滚。

## 配置要点

- `spring.kafka.producer.transaction-id-prefix`：启用事务性 Producer（多实例时需保证前缀或实例级 `transactional.id` 唯一）。
- `spring.kafka.consumer.properties.isolation.level=read_committed`：不读取未提交事务内的消息。
- `spring.datasource` + `spring.jpa`：本模块使用内存 H2；链式示例依赖 `ChainedJpaKafkaTransactionConfiguration` 中的 `transactionManager` 与 `chainedTransactionManager`。

## JDK 与测试

集成测试在 `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` 中使用 `mock-maker-subclass`，避免部分 JDK 环境下 Mockito inline 自附加失败。
