# lin-spring-sharding

Spring Boot **3.4.5** + **JPA** 下的数据库分片教学示例：**虚拟节点一致哈希（MurmurHash3）** 决定写入分片，**雪花 ID** 编码分片号以实现按主键的确定性读路由；通过 **`AbstractRoutingDataSource` + `ThreadLocal` 分片上下文 + AOP（事务前设置上下文）** 完成数据源级路由，不依赖查找表、不使用 XA 分布式事务。

## 设计要点

| 能力 | 说明 |
|------|------|
| 写路由 | `@Sharded` + SpEL 解析业务键（如 email），`ConsistentHashRing` 选择分片 |
| 读路由 | `@ShardRoutedRead` 从雪花 ID 解析 `shardId`，设置上下文后单分片查询 |
| 全局主键 | `SnowflakeIdGenerator` 生成 64 位 ID；位布局见 `SnowflakeIdCodec`（时间戳 \| 10 位分片 \| 12 位序列） |
| 连接路由 | `ShardRoutingDataSource` 在取连接时根据 `ShardContext` 选择物理库 |

### 与朴素 `hash % N` 的区别

一致哈希在增减分片时只迁移环上相邻区间对应的数据，避免「改分片数量则几乎全部键重映射」。本示例在进程内构建环，适合理解原理；生产环境还需配套再平衡、双写迁移与运维流程。

### 工程化说明（必读）

1. **JPA / Hibernate 启动**会经路由数据源取元数据连接，此时业务尚未设置 `ShardContext`。`ShardRoutingDataSource` 在 **上下文为空时回退到分片 `0`**，仅用于框架初始化；业务读写应始终通过带 `@Sharded` / `@ShardRoutedRead` 的服务方法进入，避免误写到 0 号库。
2. **切面顺序**使用 `@Order(1)`：须晚于 `ExposeInvocationInterceptor`，否则 `@Before` 中解析 `JoinPoint` 会失败；同时仍早于默认事务切面，保证「先设分片、再开事务拿连接」。
3. **方言**：路由数据源在启动期无法可靠暴露底层 JDBC URL，已配置 `hibernate.dialect` 为 H2；换 MySQL 时请改为对应方言并配置各分片 `jdbc-url`。

## 前置条件

- JDK **17**（与 `pom.xml` 中 `java.version` 一致）
- Maven 3.9+

## 快速启动

```bash
cd lin-spring-sharding
mvn spring-boot:run
```

默认使用 **3 个 H2 内存库**（`jdbc:h2:mem:shard0` … `shard2`），端口 **8080**。

## HTTP 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/users` | 创建用户；请求体 `{"email":"..."}`，响应含雪花 `id` 与 `shardId` |
| GET | `/users/{id}` | 按雪花主键查询（从 ID 解析分片） |

示例见仓库内 `api.http`。

## 配置说明（`application.yml`）

| 配置路径 | 含义 |
|----------|------|
| `app.sharding.shard-count` | 逻辑分片数量，与 `datasources` 键一致 |
| `app.sharding.virtual-nodes` | 每分片虚拟节点数（典型 64–256，默认 100） |
| `app.sharding.snowflake-epoch-ms` | 雪花时间戳纪元，需长期固定 |
| `app.sharding.datasources."0"` … | 各分片 JDBC 与账号（演示密码为空；**生产请用环境变量或外部配置，勿提交明文密钥**） |
| `spring.jpa.hibernate.ddl-auto` | 演示为 `update`；**生产建议 `none` + Flyway/Liquibase** 按分片执行迁移 |
| `spring.datasource.hikari.*` | 连接池参数，每个分片独立池 |

应用启动后，`ShardingSchemaInitializer` 会在各物理库执行演示用 DDL（`CREATE TABLE IF NOT EXISTS app_user`），避免仅默认分片被 Hibernate 更新而其他分片缺表。

## 主要代码结构

| 路径 | 职责 |
|------|------|
| `config/ShardingProperties` | `app.sharding` 配置绑定 |
| `config/ShardingDataSourceConfig` | 多分片 `DataSource`、`@Primary` 路由数据源、环与雪花 Bean |
| `config/ShardingSchemaInitializer` | 各分片建表（演示） |
| `routing/ShardContext` | 当前线程分片索引 |
| `routing/ShardRoutingDataSource` | 继承 `AbstractRoutingDataSource` |
| `hash/ConsistentHashRing` | MurmurHash3 + `TreeMap` 环 |
| `id/SnowflakeIdGenerator` / `SnowflakeIdCodec` | 发号与解析分片 |
| `aspect/ShardRoutingAspect` | `@Sharded` / `@ShardRoutedRead` 事务前路由 |
| `service/AppUserService` | 写读业务与注解用法示例 |

## 构建与测试

```bash
mvn clean test
```

包含一致哈希与雪花编解码的单元测试，以及基于随机端口的 HTTP 集成测试。

## 参考

- [AbstractRoutingDataSource](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/jdbc/datasource/lookup/AbstractRoutingDataSource.html)（Spring Framework）
- 一致哈希与雪花 ID 的常见工程组合（行业文章与内部规范较多，实现时请以团队运维与迁移方案为准）
