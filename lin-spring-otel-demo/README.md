# lin-spring-otel-demo

Spring Boot + OpenTelemetry + Micrometer + Prometheus 可观测性综合演示项目。

## 技术栈

| 组件 | 版本 | 用途 |
|---|---|---|
| Spring Boot | 3.4.4 | 应用框架 |
| Java | 17 | 运行时 |
| Micrometer | (Spring Boot 管理) | 指标门面 |
| micrometer-registry-prometheus | (Spring Boot 管理) | Prometheus 指标导出 |
| micrometer-tracing-bridge-otel | (Spring Boot 管理) | 链路追踪桥接 |
| OpenTelemetry OTLP Exporter | (Spring Boot 管理) | Trace 导出 |
| Lombok | (Spring Boot 管理) | 减少样板代码 |

## 项目结构

```
lin-spring-otel-demo/
├── pom.xml
├── docker-compose.yml
├── api.http
├── config/
│   ├── prometheus.yml                    # Prometheus 抓取配置
│   ├── otel-collector-config.yml         # OTel Collector 配置
│   └── grafana/datasources/
│       └── prometheus.yml                # Grafana 数据源自动配置
└── src/main/
    ├── java/com/lin/spring/oteldemo/
    │   ├── OtelDemoApplication.java      # 启动类
    │   ├── config/
    │   │   └── MetricsConfig.java        # Micrometer 公共标签配置
    │   ├── controller/
    │   │   └── OrderController.java      # 订单 REST API
    │   ├── model/
    │   │   ├── Order.java                # 订单模型
    │   │   └── OrderRequest.java         # 请求 DTO
    │   └── service/
    │       └── OrderService.java         # 业务逻辑 + 指标埋点
    └── resources/
        └── application.yml              # 应用配置
```

## 可观测性能力

### 自定义指标（Micrometer）

| 指标名 | 类型 | 说明 |
|---|---|---|
| `orders.created.total` | Counter | 订单创建总数（`category` 标签） |
| `orders.processing.duration` | Timer | 订单处理耗时（含百分位直方图） |
| `orders.active.count` | Gauge | 当前活跃订单数 |
| `orders.amount.summary` | DistributionSummary | 订单金额分布（含百分位直方图） |

所有指标自动附带公共标签：`application=lin-spring-otel-demo`，`env=dev`。

### 链路追踪（OpenTelemetry）

- REST 控制器端点自动创建 Span（通过 Micrometer Observation API 桥接）
- `OrderService.createOrder()` 中手动创建 `order.process` Span，附带 `order.id`、`order.amount`、`order.item.count` 属性
- 通过 OTLP gRPC 导出到 OTel Collector → Jaeger

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

### 1. 启动应用

```bash
./mvnw spring-boot:run
```

### 2. 测试 API

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

### 3. 启动可观测性基础设施

```bash
docker-compose up -d
```

启动后可访问：

| 服务 | 地址 | 说明 |
|---|---|---|
| Prometheus | http://localhost:9090 | 指标查询 |
| Grafana | http://localhost:3000 | 可视化面板（admin/admin） |
| Jaeger | http://localhost:16686 | 链路追踪查看 |

## 配置说明

### application.yml 关键配置

```yaml
management:
  endpoints.web.exposure.include: prometheus,health,info  # 暴露的端点
  tracing.sampling.probability: 1.0                        # 采样率（1.0 = 全部采样）
  otlp.tracing.endpoint: http://localhost:4317/v1/traces   # OTLP gRPC 地址
```

### 端口说明

| 端口 | 服务 |
|---|---|
| 8080 | Spring Boot 应用 |
| 9090 | Prometheus |
| 3000 | Grafana |
| 4317 | OTel Collector (gRPC) |
| 4318 | OTel Collector (HTTP) |
| 16686 | Jaeger UI |



这是一个完整的可运行 Demo。我们将创建一个标准的工程目录，包含一个简单的 Spring Boot 3 应用、OpenTelemetry Collector 和 Prometheus，并使用 `docker-compose.yml` 将它们一键启动。

为了方便测试，我们不引入外部的数据库等组件，仅专注在**可观测性数据的流转**上。

### 第一步：创建目录结构
请在你的电脑上创建一个空目录（例如 `otel-demo`），并按如下结构创建文件：

```text
otel-demo/
├── docker-compose.yml
├── otel-collector-config.yaml
├── prometheus.yml
└── spring-app/
    ├── Dockerfile
    ├── pom.xml
    └── src/main/
        ├── java/com/example/demo/DemoApplication.java
        ├── java/com/example/demo/DemoController.java
        └── resources/application.yml
```

---

### 第二步：配置基础设施 (Docker 相关文件)

**1. `docker-compose.yml`**
在根目录创建 `docker-compose.yml`：

```yaml
version: '3.8'

services:
  # 1. 我们的 Spring Boot 业务应用
  spring-app:
    build:
      context: ./spring-app
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      - otel-collector

  # 2. OpenTelemetry Collector (中央遥测枢纽)
  otel-collector:
    image: otel/opentelemetry-collector:0.95.0
    command: ["--config=/etc/otel-collector-config.yaml"]
    volumes:
      - ./otel-collector-config.yaml:/etc/otel-collector-config.yaml
    ports:
      - "4317:4317" # OTLP gRPC 接收端口
      - "4318:4318" # OTLP HTTP 接收端口 (Spring Boot 默认使用)
      - "8889:8889" # 暴露给 Prometheus 拉取的端口

  # 3. Prometheus (存储与查询 Metrics)
  prometheus:
    image: prom/prometheus:v2.50.0
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"
    depends_on:
      - otel-collector
```

**2. `otel-collector-config.yaml`**
在根目录创建 Collector 的配置文件：

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318 # 接收 Spring Boot 推送的数据

processors:
  batch:
  resource:
    attributes:
      - key: environment
        value: "demo"
        action: insert

exporters:
  prometheus:
    endpoint: "0.0.0.0:8889" # 开启 8889 端口供 Prometheus 拉取
    send_timestamps: true
  logging:
    verbosity: detailed # 在控制台打印日志和 Traces 以供调试

pipelines:
  metrics:
    receivers: [otlp]
    processors: [resource, batch]
    exporters: [prometheus, logging]
  traces:
    receivers: [otlp]
    processors: [resource, batch]
    # 本 Demo 仅展示 Prometheus，对于 Traces 我们将其输出到日志中
    # 实际生产中可替换为 Jaeger 或 Tempo 的 exporter
    exporters: [logging] 
```

**3. `prometheus.yml`**
在根目录创建 Prometheus 抓取配置：

```yaml
global:
  scrape_interval: 10s

scrape_configs:
  - job_name: 'otel-collector'
    static_configs:
      # 抓取 OTel Collector 暴露的 /metrics 端点
      - targets: ['otel-collector:8889']
```

---

### 第三步：编写 Spring Boot 应用

进入 `spring-app` 目录。

**1. `spring-app/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.4</version> </parent>
    <groupId>com.example</groupId>
    <artifactId>demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-otlp</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-tracing-bridge-otel</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**2. `spring-app/src/main/resources/application.yml`**

```yaml
spring:
  application:
    name: demo-service

management:
  endpoints:
    web:
      exposure:
        include: health,info
  metrics:
    tags:
      application: ${spring.application.name}
  otlp:
    metrics:
      export:
        # 指向同处于 Docker network 的 otel-collector
        url: http://otel-collector:4318/v1/metrics
        step: 10s
    tracing:
      export:
        url: http://otel-collector:4318/v1/traces
  tracing:
    sampling:
      probability: 1.0 # Demo 环境 100% 采样
```

**3. `spring-app/src/main/java/com/example/demo/DemoApplication.java`**

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

**4. `spring-app/src/main/java/com/example/demo/DemoController.java`**
我们创建一个简单的接口，每次调用都会产生 HTTP 请求相关的 Metrics 和 Traces。

```java
package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;

@RestController
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);
    private final Random random = new Random();

    @GetMapping("/api/hello")
    public String hello() throws InterruptedException {
        log.info("Received request for /api/hello");
        
        // 模拟业务处理耗时，这会在 Metrics 的 timer 和 Tracing span 中体现
        int sleepTime = random.nextInt(500) + 100; 
        Thread.sleep(sleepTime);
        
        return "Hello from Spring Boot! Processed in " + sleepTime + " ms";
    }
}
```

**5. `spring-app/Dockerfile`**

```dockerfile
# 使用 maven 镜像进行多阶段构建
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# 运行镜像
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/demo-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

### 第四步：一键启动与验证

回到根目录（`otel-demo`），执行以下命令：

```bash
docker-compose up -d --build
```

**验证流程：**

1. **触发业务请求：**
   打开浏览器或使用 curl 访问几次 Spring Boot 接口：
   `http://localhost:8080/api/orders`

2. **验证 OpenTelemetry Collector 接收：**
   查看 Collector 日志，你应该能看到接收到的 Metrics 和 Traces 输出（因为我们配置了 `logging` exporter）：
   `docker-compose logs -f otel-collector`

3. **验证 Prometheus 存储：**
   访问 Prometheus 自带的 Web UI：
   `http://localhost:9090`

   在搜索框中输入 `http_server_requests_seconds_count` 并点击 **Execute**。
   你将看到如下类似的数据，说明 Spring Boot 的指标已经通过 OTel Collector 成功流入了 Prometheus：
   ```text
   http_server_requests_seconds_count{application="demo-service", environment="demo", exported_job="demo-service", instance="otel-collector:8889", job="otel-collector", method="GET", status="200", uri="/api/hello"}
   ```
   *注意 `environment="demo"` 这个标签是我们通过 Collector 的 resource processor 统一加上的，应用端并没有这个配置，体现了解耦的优势。*
4. 查看可视化：

-- 打开 Grafana: http://localhost:3000。

-- 点击左侧菜单 Explore，确认数据源已选择 Prometheus。

-- 输入查询语句：http_server_requests_seconds_count，点击右上角 Run Query 即可看到波形图。

5. 推荐看板：

-- Grafana 官方有一个非常棒的 Spring Boot 仪表盘。

-- 在 Grafana 中点击 + -> Import，输入 ID 19022 (基于 Micrometer) 或 4701，即可获得专业级的监控大屏。