# lin-spring-notification

面向 **1000 万用户** 规模的通知系统演示（Java 21 + Spring Boot 3.4 + Kafka + Redis + H2）。

## 架构要点

| 模式 | 说明 |
|------|------|
| 异步解耦 | API 只做鉴权、持久化、发 Kafka，HTTP 立即返回 |
| 优先级隔离 | `notification.critical` / `transactional` / `marketing` 三个 Topic |
| 渠道隔离 | Push / Email / SMS / In-App 独立 Consumer Group |
| 有效一次 | Redis SETNX 幂等键（Kafka 模式） |
| 背压 | Provider 层 SimpleRateLimiter 限速 |
| 重试 + DLQ | 指数退避 → `notification.retry` → `notification.dlq` |

## 快速启动（本地模式，无需 Docker）

```bash
cd lin-spring-notification
./mvnw spring-boot:run
```

默认 **进程内异步投递**，端口 `8095`。API 示例见 `api.http`。

## Kafka + Redis 模式

```bash
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=kafka
```

- Kafka：`localhost:9093`
- Redis：`localhost:6380`

## HTTP 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/notifications` | 发送单条通知 |
| GET | `/api/notifications/{id}` | 查询通知状态 |
| POST | `/api/notifications/campaigns` | 批量营销（流式 Kafka） |
| GET | `/actuator/prometheus` | Prometheus 指标 |

## 配置

见 `application.properties` 与 `application-kafka.properties`。
