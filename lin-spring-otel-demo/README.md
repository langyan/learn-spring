# lin-spring-otel-demo

Spring Boot 3 + OpenTelemetry + Micrometer + Prometheus 可观测性综合演示项目。

通过一个简单的订单 API 演示 **自定义指标（Metrics）** 和 **链路追踪（Traces）** 的完整数据流转：

```
Spring Boot → (OTLP/HTTP) → OTel Collector → (Prometheus Remote Write) → Prometheus → Grafana
```

## 技术栈

| 组件 | 版本 | 用途 |
|---|---|---|
| Spring Boot | 3.4.4 | 应用框架 |
| Java | 17 | 运行时 |
| Micrometer | Spring Boot 管理 | 指标门面 |
| micrometer-registry-otlp | Spring Boot 管理 | OTLP 指标导出 |
| micrometer-tracing-bridge-otel | Spring Boot 管理 | 链路追踪桥接 OTel |
| OpenTelemetry Collector | latest | 遥测数据中枢 |
| Prometheus | latest | 指标存储与查询 |
| Grafana | latest | 可视化面板 |

## 项目结构

```
lin-spring-otel-demo/
├── pom.xml
├── Dockerfile                              # 多阶段构建镜像
├── docker-compose.yml                      # 一键启动全部基础设施
├── api.http                                # HTTP 接口测试文件
├── config/
│   ├── otel-collector-config.yml           # OTel Collector 配置
│   ├── prometheus.yml                      # Prometheus 抓取配置
│   └── grafana/datasources/
│       └── prometheus.yml                  # Grafana 数据源自动配置
└── src/main/
    ├── java/com/lin/spring/oteldemo/
    │   ├── OtelDemoApplication.java        # 启动类
    │   ├── config/
    │   │   └── MetricsConfig.java          # Micrometer 公共标签配置
    │   ├── controller/
    │   │   └── OrderController.java        # 订单 REST API
    │   ├── model/
    │   │   ├── Order.java                  # 订单模型
    │   │   └── OrderRequest.java           # 请求 DTO
    │   └── service/
    │       └── OrderService.java           # 业务逻辑 + 指标埋点
    └── resources/
        └── application.yml                # 应用配置
```

## 可观测性能力

### 自定义指标（Micrometer）

| 指标名 | 类型 | 说明 |
|---|---|---|
| `orders.created.total` | Counter | 订单创建总数（按 `category` 标签分类） |
| `orders.processing.duration` | Timer | 订单处理耗时（含百分位直方图） |
| `orders.active.count` | Gauge | 当前活跃订单数 |
| `orders.amount.summary` | DistributionSummary | 订单金额分布（含百分位直方图） |

所有指标自动附带公共标签：`application=lin-spring-otel-demo`，`env=dev`。

### 链路追踪（OpenTelemetry）

- REST 控制器端点自动创建 Span（通过 Micrometer Observation API 桥接）
- `OrderService.createOrder()` 中手动创建 `order.process` Span，附带 `order.id`、`order.amount`、`order.item.count` 属性
- 通过 OTLP HTTP 导出到 OTel Collector，由 Collector 的 `debug` exporter 输出到日志

### 数据流转架构

```
                     OTLP/HTTP (4318)
Spring Boot App ─────────────────────► OTel Collector ──► debug (日志输出)
  (Micrometer +                        │
   Observation API)                     └──► Prometheus exporter (8889)
                                              │
                                              │ scrape
                                              ▼
                                         Prometheus (9090)
                                              │
                                              │ query
                                              ▼
                                           Grafana (3000)
```

## REST API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/orders` | 创建订单 |
| GET | `/api/orders` | 查询所有订单 |
| GET | `/api/orders/{id}` | 根据 ID 查询订单 |
| PUT | `/api/orders/{id}/complete` | 完成订单 |

### Actuator 端点

| 路径 | 说明 |
|---|---|
| `/actuator/health` | 健康检查 |
| `/actuator/info` | 应用信息 |
| `/actuator/prometheus` | Prometheus 格式指标 |

## 快速开始

### 前置条件

- Java 17+
- Docker & Docker Compose

### 1. 本地开发（不使用 Docker）

```bash
./mvnw spring-boot:run
```

> 注意：本地运行时需要修改 `application.yml` 中的 OTLP 地址为 `http://localhost:4318/...`，并单独启动 OTel Collector 和 Prometheus。

### 2. Docker Compose 一键启动（推荐）

构建并启动所有服务：

```bash
docker-compose up -d --build
```

启动后各服务状态：

| 服务 | 地址 | 说明 |
|---|---|---|
| Spring Boot | http://localhost:8080 | 业务应用 |
| OTel Collector | localhost:4317 (gRPC) / 4318 (HTTP) | 遥测数据接收 |
| Prometheus | http://localhost:9090 | 指标查询 |
| Grafana | http://localhost:3000 | 可视化面板（admin/admin） |

### 3. 测试 API

使用 `api.http` 文件或 curl：

```bash
# 创建订单
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"itemName":"MacBook Pro","category":"electronics","amount":1999.99}'

# 查看所有订单
curl http://localhost:8080/api/orders

# 查看 Prometheus 指标
curl http://localhost:8080/actuator/prometheus
```

### 4. 验证可观测性数据

**验证 OTel Collector 接收数据：**

```bash
docker-compose logs -f otel-collector
```

日志中可以看到 Metrics 和 Traces 的 debug 输出。

**验证 Prometheus 指标：**

1. 打开 http://localhost:9090
2. 在查询框输入 `orders_created_total`，点击 **Execute**
3. 应能看到带有 `category`、`environment="production"` 等标签的指标数据

**验证 Grafana 可视化：**

1. 打开 http://localhost:3000，使用 admin/admin 登录
2. 进入 **Explore** 页面，数据源已自动配置为 Prometheus
3. 输入查询语句如 `orders_created_total`，点击 **Run Query** 查看波形图

**导入推荐看板：**

在 Grafana 中点击 **+ → Import**，输入以下 ID 获得专业级 Spring Boot 监控大屏：

- **19022** — 基于 Micrometer 的 Spring Boot 仪表盘
- **4701** — Spring Boot Statistics

## 配置说明

### application.yml 关键配置

```yaml
management:
  metrics:
    tags:
      application: ${spring.application.name}   # 所有指标追加应用名标签
  otlp:
    metrics:
      export:
        url: http://otel-collector:4318/v1/metrics  # OTLP 指标推送地址
        step: 10s                                    # 推送间隔
    tracing:
      export:
        url: http://otel-collector:4318/v1/traces    # OTLP 链路推送地址
  tracing:
    sampling:
      probability: 1.0   # 采样率 1.0 = 全部采样（生产环境建议调低）
```

### OTel Collector 数据处理

Collector 配置了以下处理流程：

1. **batch processor** — 批量发送，提高吞吐
2. **memory_limiter** — 限制内存使用，防止 OOM
3. **resource processor** — 统一追加 `environment=production` 标签（应用端无需配置）

### 端口说明

| 端口 | 服务 |
|---|---|
| 8080 | Spring Boot 应用 |
| 3000 | Grafana |
| 4317 | OTel Collector (gRPC) |
| 4318 | OTel Collector (HTTP) |
| 8889 | OTel Collector (Prometheus exporter) |
| 9090 | Prometheus |

## 常用命令

```bash
# 启动所有服务
docker-compose up -d --build

# 查看日志
docker-compose logs -f

# 只查看 Collector 日志
docker-compose logs -f otel-collector

# 停止所有服务
docker-compose down

# 重新构建应用镜像
docker-compose build spring-app
```
