# lin-spring-kafka-transactions

演示 **Kafka 生产者事务**（`KafkaTemplate.executeInTransaction`）与 **`read_committed`** 消费者：同一事务内向 `demo-tx-a`、`demo-tx-b` 发送两条消息，要么提交后均被消费者看到，要么在业务异常时中止且消费者均不可见。

参考阅读（正文为会员内容，抓取工具仅能访问引言）：[Understanding Apache Kafka Transactions (Medium)](https://medium.com/codefarm-java-ecosystem/understanding-apache-kafka-transactions-guarantees-boundaries-and-a-practical-spring-boot-a0a0dda8f765)

与 **数据库 + Kafka** 的跨系统一致性边界不同；若需「同事务写库 + 发消息」，见仓库内 `lin-spring-outbox`（Transactional Outbox）。

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
   ```

3. 启动应用（默认端口 `8095`）：

   ```bash
   mvn spring-boot:run
   ```

4. 使用 `api.http` 或 curl 调用：

   - `POST /api/tx/commit/{correlationId}` — 提交事务，监听器应收到两条已提交消息。
   - `POST /api/tx/rollback/{correlationId}` — 发送后失败中止，**不应**在应用日志/监听队列中出现该 `correlationId` 的消息。

## 配置要点

- `spring.kafka.producer.transaction-id-prefix`：启用事务性 Producer（多实例时需保证前缀或实例级 `transactional.id` 唯一）。
- `spring.kafka.consumer.properties.isolation.level=read_committed`：不读取未提交事务内的消息。

## JDK 与测试

集成测试在 `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` 中使用 `mock-maker-subclass`，避免部分 JDK 环境下 Mockito inline 自附加失败。
