# lin-spring-grpc

用 Spring Boot + gRPC 构建高性能微服务的验证项目：协议缓冲区契约、一元 RPC、服务器流式 RPC、错误处理（gRPC Status → HTTP 映射）、Deadline，以及 gRPC 与 REST 的对比。

## 架构

```
lin-spring-grpc/
├── lin-spring-grpc-proto/    # 共享契约：payment.proto + 生成的消息类与 gRPC 存根
├── lin-spring-grpc-server/   # gRPC 服务端（端口 9090）：@GrpcService 实现 PaymentService
└── lin-spring-grpc-client/   # REST 网关（端口 8081）：@GrpcClient 注入 BlockingStub，内部走 gRPC
```

```
HTTP/JSON 客户端 ──> lin-spring-grpc-client (REST 8081) ──gRPC/HTTP2+Protobuf──> lin-spring-grpc-server (9090)
```

## 技术栈

| 组件 | 版本 | 说明 |
|---|---|---|
| Spring Boot | 3.5.3 | Java 21 |
| grpc-server/client-spring-boot-starter (net.devh) | 3.1.0.RELEASE | @GrpcService / @GrpcClient 注解集成 |
| io.grpc | 1.63.0 | 与 starter 传递依赖对齐 |
| protobuf-java | 3.25.3 | 同上 |
| protobuf-maven-plugin (xolstice) | 0.6.1 + os-maven-plugin 1.7.1 | mvn compile 时生成存根 |

## 契约（lin-spring-grpc-proto/src/main/proto/payment.proto）

```protobuf
service PaymentService {
  rpc GetPayment(PaymentRequest) returns (PaymentResponse);              // 一元
  rpc ListPayments(ListPaymentsRequest) returns (stream PaymentResponse); // 服务器流
}
```

`java_package = com.lin.spring.grpc.proto`；`mvn compile` 生成 `PaymentRequest`、`PaymentResponse`、`PaymentServiceGrpc` 等类。

## 构建与运行

```powershell
# 父目录构建（proto 模块先于 server/client 生成存根）
mvn clean install

# 终端 1：启动 gRPC 服务端（9090）
cd lin-spring-grpc-server; mvn spring-boot:run

# 终端 2：启动 REST 网关（8081）
cd lin-spring-grpc-client; mvn spring-boot:run
```

## 验证场景（见 api.http）

| 场景 | 请求 | gRPC Status | HTTP |
|---|---|---|---|
| 一元成功 | `GET /api/payments/PAY-001` | OK | 200 |
| 不存在 | `GET /api/payments/PAY-999` | NOT_FOUND | 404 |
| 空 ID | `GET /api/payments/%20` | INVALID_ARGUMENT | 400 |
| Deadline（服务端 SLOW- 沉睡 5s，客户端截止 2s） | `GET /api/payments/SLOW-001` | DEADLINE_EXCEEDED | 504 |
| 服务器流 | `GET /api/payments?userId=USER-1&pageSize=10` | OK（多条流式推送） | 200 |
| 流分页 | `GET /api/payments?userId=USER-1&pageSize=2` | OK | 200（2 条） |
| 服务端宕机 | 停掉 lin-spring-grpc-server 后查询 | UNAVAILABLE | 503 |

种子数据：PAY-001..PAY-003（USER-1）、PAY-004..PAY-005（USER-2）。

## 关键实现点

- **服务端模式**：验证请求 → 干活 → `onNext`（一元一次/流式多次）→ `onCompleted`；失败改调 `onError(Status.XXX.withDescription(...).asRuntimeException())`
- **客户端 Deadline**：每次调用 `stub.withDeadlineAfter(2, TimeUnit.SECONDS)`，防止下游挂起拖垮网关
- **错误映射**：`GrpcExceptionMapper` 把 gRPC 状态码翻译为 HTTP（400/401/403/404/409/503/504/500），响应体带 `grpcStatus` 字段
- **流式消费**：BlockingStub 的 `listPayments` 返回 `Iterator<PaymentResponse>`，`forEachRemaining` 聚合

## gRPC vs REST

| 维度 | REST (HTTP/1.1 + JSON) | gRPC (HTTP/2 + Protobuf) |
|---|---|---|
| 序列化 | JSON 文本，字段名重复传输 | Protobuf 二进制，小 3–10 倍，解析快 20–100 倍 |
| 传输 | 每连接一个请求，队头阻塞 | 单 TCP 连接多路复用流 + 头部压缩 |
| 代码生成 | 可选（OpenAPI，易漂移） | 内置且强制（.proto 即契约） |
| 流式 | 事后补（SSE/WebSocket） | 原生 4 种模式 |
| 截止时间 | 无跨服务传播 | 内建跨服务传播 |
| 浏览器 | 原生 | 需要 grpc-web 代理 |
| 调试 | curl 即可 | 二进制，需 grpcurl 等工具 |

**选型**：内部服务间通信、高吞吐低延迟、流式、多语言 → gRPC；面向浏览器的公共 API、简单 CRUD、团队熟悉度 → REST。常见折中：对外 REST、对内 gRPC（本项目的 lin-spring-grpc-client 即这种"网关"模式）。

## 测试

```powershell
mvn test
```

- `PaymentGrpcServiceTest`（lin-spring-grpc-server）：InProcess 传输直连服务实现，覆盖一元成功/INVALID_ARGUMENT/NOT_FOUND、流式有序/分页/空流
- `GrpcExceptionMapperTest`（lin-spring-grpc-client）：状态码映射与错误响应体

## 常见问题（本项目已踩过/已规避）

| 问题 | 原因与解决 |
|---|---|
| `NoSuchMethodError: io.grpc.CallOptions.getOnReadyThreshold()` / `Panic! This is a bug!` | classpath 上 grpc 各构件版本不一致（如 stub 1.62.2 混 net.devh 传递的 core 1.63.0）→ 父 POM 统一锁定 `grpc.version=1.63.0`，与 starter 传递版本完全对齐 |
| `找不到符号: 类 Generated (javax.annotation)` | 生成代码引用 JDK 9+ 移除的 API → lin-spring-grpc-proto 模块加 `org.apache.tomcat:annotations-api`（provided） |
| `Could not find protoc` / os 分类器缺失 | 缺 `os-maven-plugin` 扩展 → lin-spring-grpc-proto 模块 `<extensions>` 已配置 |
| 存根未生成 | protobuf 插件必须同时配 `compile` 与 `compile-custom` 两个 goal |
| proto 类 ClassNotFoundException | 未从父目录按序构建 → 用 `mvn clean install`（reactor 保证 lin-spring-grpc-proto 先构建） |
| `UNAVAILABLE: io exception` | 服务端未启动或端口不匹配 → 核对 `grpc.server.port=9090` 与客户端 `static://localhost:9090` |
| `DEADLINE_EXCEEDED` | 下游太慢 → 调大 `withDeadlineAfter` 或优化服务逻辑（本项目的 SLOW- 场景即故意触发） |
| negotiation-type 错误 | 本地开发用 `plaintext`，生产 TLS |
