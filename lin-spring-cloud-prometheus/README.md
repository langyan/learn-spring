# lin-spring-cloud-prometheus

Spring Boot + Micrometer + Prometheus + Grafana 监控示例项目。

通过一个简单的订单服务，演示如何使用 Micrometer 采集自定义指标，并通过 Prometheus 抓取、Grafana 可视化。

## 技术栈

- Spring Boot 3.4.4 (Java 21)
- Spring Boot Actuator
- Micrometer (Prometheus Registry)
- Prometheus
- Grafana

## 自定义指标

本项目通过 OrderService 演示了三种核心指标类型：

| 指标类型 | 指标名称 | 说明 |
|---------|---------|------|
| Counter | `orders_created_total` | 累计创建的订单总数，只增不减 |
| Gauge | `orders_active` | 当前活跃订单数，可增可减 |
| Timer | `orders_create_time_seconds` | 创建订单的耗时分布 |
| Timer | `orders_get_time_seconds` | 查询订单的耗时分布 |

## 快速开始

### 1. 启动应用

```bash
cd lin-spring-cloud-prometheus
mvn spring-boot:run
```

应用启动在 `http://localhost:8080`。

### 2. 调用 API 产生指标数据

```bash
# 创建订单
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"productId": "PROD-001"}'

# 查询订单 (用返回的 orderId 替换)
curl http://localhost:8080/orders/ORD-1234567890

# 完成订单
curl -X DELETE http://localhost:8080/orders/ORD-1234567890
```

### 3. 查看 Prometheus 格式指标

```bash
curl http://localhost:8080/actuator/prometheus
```

### 4. 启动 Prometheus + Grafana

```bash
docker-compose up -d
```

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (账号 `admin` / 密码 `admin`)

Grafana 已自动配置 Prometheus 数据源，可直接创建 Dashboard 查询项目指标。

## 项目结构

```
lin-spring-cloud-prometheus/
├── pom.xml                                    # Maven 配置
├── docker-compose.yml                         # Prometheus + Grafana 容器编排
├── prometheus/
│   └── prometheus.yml                         # Prometheus 采集配置
├── grafana/
│   └── provisioning/datasources/
│       └── datasource.yml                     # Grafana 数据源自动配置
├── src/main/java/com/lin/spring/prometheus/
│   ├── PrometheusApplication.java             # 启动类
│   ├── config/
│   │   └── MetricsConfig.java                 # 指标配置 (@Timed 切面 + Counter)
│   ├── controller/
│   │   └── OrderController.java               # 订单 REST API
│   └── service/
│       └── OrderService.java                  # 订单服务 (Counter/Gauge/@Timed)
├── src/main/resources/
│   └── application.yml                        # 应用配置
└── api.http                                   # HTTP 接口测试文件
```

## 关键配置说明

### application.yml

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus   # 暴露 Actuator 端点
  metrics:
    tags:
      application: ${spring.application.name}     # 所有指标附加应用名标签
  endpoint:
    prometheus:
      enabled: true
```

### prometheus.yml

Prometheus 每 5 秒从 Spring Boot Actuator 采集一次指标：

```yaml
scrape_configs:
  - job_name: 'spring-boot-app'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']
```

## Grafana 常用查询 (PromQL)

```promql
# 每秒创建订单数
rate(orders_created_total{application="lin-spring-cloud-prometheus"}[1m])

# 当前活跃订单数
orders_active{application="lin-spring-cloud-prometheus"}

# 创建订单 P99 耗时
histogram_quantile(0.99, rate(orders_create_time_seconds_bucket{application="lin-spring-cloud-prometheus"}[5m]))

# HTTP 请求 QPS
rate(http_server_requests_seconds_count{application="lin-spring-cloud-prometheus"}[1m])
```
