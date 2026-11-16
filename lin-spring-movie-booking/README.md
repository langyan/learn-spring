# lin-spring-movie-booking

电影订票系统（BookMyShow 式）微服务参考实现：**10 万用户、300 个座位、一个数据库 —— 零超卖**。

设计蓝本：Medium《100k users, 300 seats, one database: how would you design BookMyShow?》。
姊妹项目：`lin-spring-ticket-race`（PostgreSQL + 三种锁策略对比）；本项目忠实采用 **MySQL 语义**（条件 UPDATE、DB 时钟 `NOW(3)`、聚簇主键）。

## 核心理念

> 浏览可以陈旧（座位图 3 秒缓存），座位分配必须强一致（MySQL 条件 UPDATE 权威判定）。

**三层座位保护**，任何一层失守，下一层兜底：

| 层 | 机制 | 角色 |
|---|---|---|
| L1 | Redis `SET NX PX` 建议性锁 | 挡掉 ~90% 争用；Redis 宕机正确性不受影响 |
| L2 | `show_seats` 条件 UPDATE（原生 SQL） | **权威判定**：受影响行数 ≠ 请求数即冲突 |
| L3 | `booking_seats` 账本 `UNIQUE(show_id, seat_id)` | 结构性兜底：应用层任何 bug 都无法双重出票 |

```sql
-- L2 原子占座：可占 = 空闲 或 已过期的 HELD（正确性不依赖清扫器）
UPDATE show_seats
   SET status='HELD', hold_id=:holdId, hold_expires_at=:expiresAt,
       booking_id=:bookingId, version=version+1
 WHERE show_id=:showId AND seat_id IN (:seatIds)
   AND (status='AVAILABLE' OR (status='HELD' AND hold_expires_at<=NOW(3)));
```

### 关键机制速览

- **Hold-and-pay 生命周期**：hold 5 分钟 → 进支付延长一次至 10 分钟 → webhook 确认 / 超时过期。
- **迟到 webhook**（最难竞态）：清扫器已放座但钱付成功 → 60 秒有界宽限窗口内 `confirmOrReclaimSeats` 尝试收回座位确认；失败则**退款** + `seats_lost_after_capture` 告警。
- **UNKNOWN 支付**：绝不假设 —— 只能显式查网关状态端点解锁。
- **幂等**：入口按 `(user_id, idempotency_key)` 同事务缓存响应；webhook 按 `(gateway, gateway_ref)` 唯一约束天然去重（5 次投递一次生效）。
- **Transactional Outbox + Inbox**：确认事务内写 `outbox_events` → 轮询中继（条件 UPDATE 原子申领，多实例安全）发 Kafka → 消费端 `processed_events` 唯一键幂等；指数退避，3 次失败进死信（`/api/v1/admin/outbox/replay` 人工重放）。Kafka 只承载通知类事件，**绝不在订票/支付关键路径上**。
- **事务边界铁律**：`@Transactional` 内绝不调外部系统 —— 支付网关调用发生在两个事务之间（`TransactionTemplate` 编排）。
- **防死锁**：所有座位操作前 `seatIds` 去重 + 排序；单次 1–10 座。
- **时钟**：DB 时钟 `NOW(3)` + JDBC 会话 UTC，规避多实例时钟偏移。

## 模块

```
lin-spring-movie-booking
├── -common          公共 DTO / 枚举
├── -discovery       Eureka :8762
├── -gateway         Spring Cloud Gateway :8180（/api/v1 统一入口）
├── -movie-service   目录/场次/座位图（Redis 3 秒缓存）:8191
├── -booking-service 核心：三层保护/hold-pay/幂等/清扫/Outbox 中继/Inbox 消费 :8192
├── -payment-service 模拟 PSP：NORMAL/FAIL/LATE/DUPLICATE/LOST 五场景 :8193
└── -simulator       并发竞态模拟器 :8194
```

基础设施：`compose.yml` — MySQL 8.4 `:3307`、Redis 7.4 `:6380`、Kafka (KRaft) `:29092`。

## 启动

```powershell
# 1. 基础设施
docker compose up -d

# 2. 打包
mvn package -DskipTests

# 3. 依序启动（每个一条终端）
#    演示 LATE 场景需把 hold TTL 缩短（payment 网关约 7 秒后才回调）：
#    PowerShell: $env:HOLD_TTL_SECONDS="5"; java -jar ...
java -jar lin-spring-movie-booking-discovery/target/*-SNAPSHOT.jar
java -jar lin-spring-movie-booking-gateway/target/*-SNAPSHOT.jar
java -jar lin-spring-movie-booking-movie-service/target/*-SNAPSHOT.jar
$env:HOLD_TTL_SECONDS="5"; java -jar lin-spring-movie-booking-booking-service/target/*-SNAPSHOT.jar   # 宽限 60 秒
java -jar lin-spring-movie-booking-payment-service/target/*-SNAPSHOT.jar
java -jar lin-spring-movie-booking-simulator/target/*-SNAPSHOT.jar
```

movie-service 首启自动种子化目录（2 部影片 / 300+96 座 / 12 场次）；booking-service 首启自动发布全部场次库存（Flyway 建表）。

## 演示主线（详见 `api.http`）

1. **浏览**：`GET /api/v1/movies` → 场次 → 座位图（3 秒缓存）。
2. **占座**：`POST /api/v1/bookings/hold`；同 key 重放返回同一 booking；他人抢同座 409。
3. **支付五场景**（`scenario` 字段切换）：
   - `NORMAL` — 2 秒后成功回调，自动 CONFIRMED；
   - `DUPLICATE` — webhook 连投 5 次，一次生效；
   - `FAIL` — 座位立即释放；
   - `LATE` — 回调晚于 hold 过期 → 宽限窗口收回座位；
   - `LOST` — 回调丢失 → `POST /bookings/{ref}/payments/{gatewayRef}/sync` 显式查询解锁。
4. **看板**：`GET /api/v1/admin/metrics/race-summary` — **`oversellDetected` 必须恒为 0**。
5. **并发实证**：
   ```http
   POST http://localhost:8194/api/v1/simulator/race
   {"showId":1,"seatIds":[201,202,203,204],"concurrency":50,"staggerMs":50}
   ```
   预期：`succeeded=1, conflicts=49, oversellDetected=0`。

## 测试

```powershell
mvn test -pl lin-spring-movie-booking-booking-service -am "-Dtest=HoldRaceIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false"
```

`HoldRaceIntegrationTest`（Testcontainers 真实 MySQL + Redis）三连证：
1. **24 并发抢同 6 座**：恰好 1 赢家、23 个 409、库内恰好一个 hold 持有全部 6 行（L1 被 mock 放行，竞态直落 L2 条件 UPDATE —— 证明权威层独立成立）。
2. **过期 HELD 立即可回收**：不等清扫器。
3. **双击幂等**：同 key 两次请求一个 booking 行。

> Windows + Docker Desktop 29：若 Testcontainers 报 npipe 400，先 `$env:DOCKER_HOST="npipe:////./pipe/dockerDesktopLinuxEngine"`；仍失败则 `docker compose up -d` 起基础设施后跑 `api.http` 演示主线（端到端实证）。

## 关键决策 FAQ

- **为何原生 SQL 而非 JPQL？** 批量 UPDATE 的 version 自增在 JPA 不可靠；`NOW(3)` 用 DB 时钟避免实例间漂移；热路径本就该一条原子语句。
- **为何 `booking_id` 在占座时就写入 `show_seats`？** 清扫器释放座位时保留它，迟到 webhook 才能追溯"这个 booking 当初要的座位集"（宽限恢复的前提）。
- **部分座位被转售后另一部分还在？** 宽限回收是原子的：`confirmOrReclaimSeats` 受影响行数不足即整体回滚，绝不出半套票。
- **为何 Outbox 而非直接发 Kafka？** 双写问题：DB 提交与 broker ack 无法原子化；Outbox 与业务同事务，中继负责"至少一次"投递，Inbox 负责"至多一次"生效。
