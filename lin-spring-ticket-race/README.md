# lin-spring-ticket-race

一个 PostgreSQL 优先的抢票并发教学项目，重点演示：

- 同一座位高并发抢购时的 race condition
- `PESSIMISTIC`、`OPTIMISTIC`、`REDIS_HOLD` 三种锁策略对比
- `hold-and-pay` 流程、支付确认、支付失败与超时释放
- 指标统计、并发模拟与可观察性

## 模块

- `lin-spring-ticket-race-common`: 公共 DTO 与枚举
- `lin-spring-ticket-race-discovery`: Eureka 注册中心
- `lin-spring-ticket-race-gateway`: 统一入口与路由转发
- `lin-spring-ticket-race-event-service`: 演出与座位只读查询
- `lin-spring-ticket-race-booking-service`: 占座、确认、取消、过期释放
- `lin-spring-ticket-race-payment-service`: 支付模拟
- `lin-spring-ticket-race-simulator`: 并发请求模拟器

## 运行依赖

- Java 21
- Maven Wrapper 或 Maven 3.9+
- PostgreSQL
- Redis（仅 `REDIS_HOLD` 策略需要）

项目根目录提供 `compose.yml`，可快速启动依赖服务。

## 推荐启动顺序

1. `docker compose up -d`
2. 启动 `lin-spring-ticket-race-discovery`
3. 启动 `lin-spring-ticket-race-gateway`
4. 启动 `lin-spring-ticket-race-event-service`
5. 启动 `lin-spring-ticket-race-payment-service`
6. 启动 `lin-spring-ticket-race-booking-service`
7. 启动 `lin-spring-ticket-race-simulator`

## 网关入口

- Gateway: `http://localhost:8080`
- Eureka Dashboard: `http://localhost:8761`

网关已内置以下路由：

- `/api/events`、`/api/shows/**` -> `event-service`
- `/api/bookings/**`、`/api/admin/metrics/**`、`/api/admin/expiry/**` -> `booking-service`
- `/api/payments/**` -> `payment-service`
- `/api/admin/simulator/**` -> `simulator`

## 演示主线

1. 通过网关调用 `/api/events` 查询演出和座位
2. 通过网关调用 `/api/bookings/hold`
3. 使用返回的 `bookingNo` 调用 `/api/bookings/{bookingNo}/pay`
4. 查看 `/api/admin/metrics/race-summary`
5. 调用 `/api/admin/simulator/run` 模拟并发抢票
