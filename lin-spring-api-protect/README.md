# lin-spring-api-protect

面向生产基线的 Spring Boot API 安全示例项目，聚焦 6 类常见攻击防护：

- SQL 注入
- 跨站脚本（XSS）
- 跨站请求伪造（CSRF）
- 分布式拒绝服务（DDoS）
- 认证攻击
- 数据暴露漏洞

项目采用 JWT 无状态认证，不依赖 Cookie 或 Session，适合作为纯后端 API 的安全起点。

## 技术栈

- Spring Boot 3.5.3
- Spring Security
- Spring Data JPA
- Spring Validation
- Spring Boot Actuator
- Micrometer + Prometheus
- H2 Database
- JWT (`jjwt`)

## 风险与防护映射

| 风险 | 防护方案 |
|------|----------|
| SQL 注入 | 统一使用 JPA Repository 和参数绑定查询，限制排序字段白名单 |
| XSS | 对 `displayName`、`bio` 等文本输入进行 HTML escape，避免恶意脚本原样回传 |
| CSRF | JWT 只走 `Authorization` 头，不放 Cookie；关闭 Session；严格 CORS；校验非法 `Origin` |
| DDoS | 登录、普通接口、管理员接口分级限流；超限返回 `429` |
| 认证攻击 | `BCrypt` 密码哈希、登录失败计数、临时锁定、统一错误消息、角色授权 |
| 数据暴露 | DTO 输出、邮箱脱敏、异常响应不暴露堆栈/SQL、Actuator 最小暴露 |

## 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| `user` | `Password123!` | `USER` |
| `admin` | `Admin123!` | `ADMIN` |

## 快速开始

```bash
cd lin-spring-api-protect
mvn spring-boot:run
```

应用默认启动在 `http://localhost:8080`。

## 关键接口

- `POST /api/auth/login`：登录获取 JWT
- `GET /api/users/me`：获取当前用户资料
- `PUT /api/users/profile`：更新资料并触发 XSS 清洗
- `GET /api/protected/ping`：基础受保护接口
- `GET /api/admin/users`：管理员查询用户列表
- `GET /api/admin/audit`：管理员审计接口
- `GET /actuator/prometheus`：查看安全指标

## JWT 与 CSRF 说明

本项目是纯 API 型后端，JWT 只通过 `Authorization: Bearer <token>` 传递，不放 Cookie，所以浏览器不会自动跨站携带凭证。这里的 CSRF 防护重点是：

- 禁用 Session
- 禁用表单登录
- 配置允许来源白名单
- 对带 `Origin` 的写请求进行来源校验

如果未来改成 Cookie + Session 登录，则必须重新启用 Spring Security CSRF Token。

## 限流策略

- 登录接口：每个 IP / 用户组合 60 秒最多 5 次
- 普通 API：每个 IP / 用户组合 60 秒最多 60 次
- 管理接口：每个 IP / 用户组合 60 秒最多 20 次

超限会返回 `429 Too Many Requests`，并记录 Prometheus 指标。

## 安全指标

项目暴露以下自定义指标：

- `security_auth_success_total`
- `security_auth_failure_total`
- `security_auth_lock_total`
- `security_rate_limit_block_total`

查看方式：

```bash
curl http://localhost:8080/actuator/prometheus
curl http://localhost:8080/actuator/metrics/security.auth.failure.total
```

## 代码结构

```text
lin-spring-api-protect/
├── pom.xml
├── README.md
├── api.http
├── src/main/java/com/lin/spring/apiprotect/
│   ├── config/
│   ├── controller/
│   ├── dto/
│   ├── exception/
│   ├── filter/
│   ├── model/
│   ├── repository/
│   └── service/
└── src/test/java/com/lin/spring/apiprotect/
```

## 生产落地建议

- 把 `app.security.jwt.secret` 改为环境变量或密钥管理服务
- 在网关层、WAF、CDN 再加一层限流和清洗
- 把 H2 替换成正式数据库
- 把用户、角色、审计日志接入真实身份体系
