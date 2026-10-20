# lin-spring-webhook

用真实代码复现 **Stripe 风格 Webhook 投递系统**，重点不是快乐路径，而是面试官在白板上用来「拆架构」的四个追问。

内部服务提交事件 → 查商户注册的 Webhook URL → 向商户端点 POST 投递。设计指标：**全球每秒约 10,000 条事件**，「内部一旦接受，就保证会尝试投递」。

技术栈：Java 21 + Spring Boot 3.4.4 + Kafka + Redis + PostgreSQL + Resilience4j + Apache HttpClient 5。

## 架构要点

| 模式 | 说明 | 代码位置 |
|------|------|----------|
| 事件接入解耦 | API 只做校验与落库，立即返回 202，事件经 Kafka 异步投递 | `service/EventIngestionService`、`publisher/KafkaEventBus` |
| 目的地解析 | 查 Postgres 找商户端点，带 30s TTL 本地缓存，避免每条事件打库 | `service/EndpointRegistryService` |
| 主队列 + 延迟队列 | Redis LIST 做就绪队列，ZSET（score = 下次投递时间）做延迟队列 | `queue/RedisDeliveryQueue` |
| 固定 worker 池 | 投递线程数固定，正是噪声邻居会打满的资源 | `dispatcher/DeliveryWorkerPool`、`config/AsyncConfig` |
| 指数退避 + DLQ | 1min→5min→1h…最长 3 天，重试回队尾，超限进 DLQ 可重放 | `service/RetryPolicyService`、`controller/DeliveryController` |
| 租户隔离 | 每商户并发配额，拿不到立即回延迟队列，绝不阻塞线程 | `isolation/PerMerchantConcurrencyGuard` |
| 目的地熔断 | 按 endpointId 动态创建断路器，跳闸后自动转入延迟队列 / 取消 | `isolation/DestinationCircuitBreakerRegistry`、`MerchantLaneRouter` |
| SSRF + DNS Rebinding 防护 | 只解析一次 DNS，把校验通过的 IP 固定给 HTTP 客户端 | `security/SsrfGuard`、`DnsPinningResolver`、`PinRegistry` |
| 事件级幂等 | Redis SETNX 幂等键 + 载荷/请求头携带唯一事件 ID | `service/IdempotencyService`、`delivery/SignatureSigner` |
| 可观测性 | Micrometer 计数器与耗时 Timer，Prometheus 暴露，投递流水可查 | `service/DeliveryOrchestrator`、`controller/MerchantHealthController` |

## 四个面试追问 ↔ 代码位置

| 追问 | 面试官的标准答案 | 本模块的实现 |
|------|----------------|--------------|
| **1 无响应的商家**（端点恒定 500） | 指数退避重试，1 分钟 / 5 分钟 / 1 小时，最多 3 天；重试回到队尾不阻塞新事件 | `RetryPolicyService#nextDelaySeconds`（60/300/3600/10800/21600/43200/86400 秒 + ±20% 抖动）；`DeliveryOrchestrator` 失败后 `queue.pushDelayed(...)` 而非线程内 sleep |
| **2 噪声邻居**（慢商户 30s 超时打满 worker 池） | 租户隔离 + 断路器：为**该目的地**跳闸，事件临时导入专门的延迟队列，完全离线则直接取消 | `PerMerchantConcurrencyGuard`（每商户信号量配额）+ `DestinationCircuitBreakerRegistry`（按 endpointId 建断路器）+ `MerchantLaneRouter`（MAIN / SLOW / QUARANTINE 三泳道自动路由） |
| **3 安全边界**（SSRF 与 DNS Rebinding） | HTTP 客户端**不得执行第二次 DNS 解析**；校验阶段解析出的 IP 直接固定给客户端，Host 头传原始域名 | `SsrfGuard#validateAndPin` 只解析一次并写入 `PinRegistry`；`DnsPinningResolver` 作为 HttpClient 的 DnsResolver，只返回已固定 IP，无 pin 直接抛异常；`PublicIpPolicy` 拦截 RFC1918 / loopback / link-local / CGNAT / 云元数据 / IPv6 ULA 等全部私有保留网段 |
| **4 完全一次交付** | 网络层 exactly-once 不可能，只能承诺 at-least-once；幂等责任在接收方，载荷带唯一事件 ID | `WebhookEvent.eventId` + `X-Webhook-Id` 请求头；平台侧 `IdempotencyService` 去重；`MockReceiverController` 的 `idempotent` 模式演示接收端按 ID 去重（重复投递返回 `duplicate_ignored`） |

### 为什么重试用延迟队列而不是 `Thread.sleep`

退避等待如果发生在 worker 线程里，1 小时的重试就会白白占住一个投递线程 —— 这正是「慢商户拖垮池子」的同类错误。本模块把等待放进 Redis ZSET（score = 到期时间戳），由 `RetryScheduler` 到期捞回主队列，等待期间**零线程占用**。

## 快速启动

### 生产形态（Kafka + Redis + PostgreSQL）

```bash
cd lin-spring-webhook
docker compose up -d
./mvnw spring-boot:run
```

端口：应用 `8096`、PostgreSQL `5434`、Redis `6381`、Kafka `9094`（均避让仓库其它模块已占用的端口）。

### 免 Docker 演示（local profile：H2 + 进程内队列）

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

local profile 会排除 Kafka / Redis 自动配置，改用 H2 与进程内队列，其余逻辑完全一致。

### 测试

```bash
./mvnw test   # 34 个测试，无需 Docker
```

## 演示脚本

启动后按 `api.http` 里的顺序执行，或用下面的命令：

```bash
# 追问 1：发给恒定 500 的商户，观察 1 分钟 / 5 分钟退避
curl -X POST http://localhost:8096/api/events -H "Content-Type: application/json" \
  -d '{"merchantId":"mch_broken","eventType":"charge.succeeded","payload":"{\"amount\":1000}"}'

# 追问 2：慢商户（接受连接后挂起 30 秒）
curl -X POST http://localhost:8096/api/events -H "Content-Type: application/json" \
  -d '{"merchantId":"mch_slowpoke","eventType":"charge.succeeded","payload":"{}"}'

# 追问 2：同时给健康商户发事件 —— 它不会被慢商户堵住
curl -X POST http://localhost:8096/api/events -H "Content-Type: application/json" \
  -d '{"merchantId":"mch_healthy","eventType":"customer.password_reset","payload":"{}"}'

# 追问 3：注册一个指向内网的 URL，应返回 400 SSRF_BLOCKED
curl -X POST http://localhost:8096/api/merchants/mch_evil/endpoints -H "Content-Type: application/json" \
  -d '{"url":"http://169.254.169.254/latest/meta-data/"}'

# 追问 4：重复投递被接收端安全忽略
curl http://localhost:8096/api/mock/stats
```

## HTTP 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/events` | 提交内部事件（202，异步投递） |
| GET | `/api/events/{eventId}` | 查询事件状态 |
| GET | `/api/events/{eventId}/attempts` | 查询全部投递尝试流水 |
| POST | `/api/merchants/{merchantId}/endpoints` | 注册 Webhook 端点（注册即做 SSRF 校验） |
| GET | `/api/merchants/{merchantId}/endpoints` | 查询端点列表 |
| GET | `/api/merchants/{merchantId}/health` | 商户泳道、断路器状态、在途数 |
| GET | `/api/merchants/{merchantId}/health/global` | 全部断路器状态与队列深度 |
| GET | `/api/dlq` | 死信队列 |
| POST | `/api/dlq/{eventId}/replay` | 重放死信事件 |
| POST | `/api/mock/receiver/{mode}` | Mock 接收端，`mode` = `ok` / `fail500` / `hang30s` / `slow` / `idempotent` |
| GET | `/api/mock/stats` | Mock 接收端统计（含 `duplicatesIgnored`） |
| POST | `/api/mock/reset` | 重置 Mock 接收端 |
| GET | `/actuator/prometheus` | Prometheus 指标 |

## 关键配置（`app.webhook.*`）

| 配置项 | 默认值 | 含义 |
|--------|--------|------|
| `retry.backoff-seconds` | `60,300,3600,10800,21600,43200,86400` | 指数退避序列 |
| `retry.max-attempts` / `retry.max-duration-days` | `8` / `3` | 最大尝试次数与最长重试窗口 |
| `delivery.connect-timeout-ms` / `response-timeout-ms` | `2000` / `5000` | 严格超时，防慢商户拖死连接 |
| `delivery.worker-threads` | `8` | 固定投递线程池大小 |
| `isolation.per-merchant-max-concurrency` | `3` | 每商户并发配额 |
| `circuit.failure-rate-threshold` / `max-offline-minutes` | `50` / `30` | 熔断阈值与判定「彻底离线」的时长 |
| `ssrf.allowed-hosts` / `allowed-ports` | `localhost` / 空 | 主机与端口白名单（生产环境 `allowed-hosts` 应为空） |
| `idempotency.ttl-hours` | `72` | 幂等键 TTL |

> 注：`ssrf.allowed-hosts=localhost` 只是为了放行本模块自带的 Mock 接收端。真实部署时应清空该列表，此时 `http://127.0.0.1/...`、`http://169.254.169.254/...` 等一律会被拦截。
