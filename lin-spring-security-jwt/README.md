# lin-spring-security-jwt

Spring Boot 4 + Spring Security 7 + JWT 认证授权生产级 Demo：**瘦 Token 无状态认证 + Redis 权限缓存 + RBAC 双层授权**，H2 内存库开箱即用。

## 架构：为什么是「瘦 Token + Redis」？

若把全部 Authority（角色/权限）写进 JWT claims，权限每多一条 token 约增大 15 字节——300 个权限时 token 膨胀到 5KB+，逼近 Nginx/Tomcat Header 上限、超出 Cookie 4093B 限制，且权限变更无法对已签发 token 生效。

本模块采用**方案 B（业界主流）**：

| 关注点 | 实现 |
|---|---|
| token 体积 | **恒定 ~150 字节**：仅 `sub` + `iat` + `exp`，与权限数量无关 |
| 权限装配 | `JwtAuthenticationFilter` 验签后经 `AuthorityResolver` 取权限：**Redis 命中直接返回（~0.1ms），未命中查库并回填**（cache-aside，TTL 10 分钟） |
| 权限时效 | 变更 DB 权限后 **evict 缓存即对已签发 token 生效**（无需重新登录/等待过期） |
| Redis 故障 | **自动降级直查数据库**（`DataAccessException` 捕获），可用性优先，仅性能受损 |
| 验签 | 仍为纯无状态本地验签（HS256），无 session |

### 认证授权数据流

```
登录: 账号密码 -> DaoAuthenticationProvider(BCrypt, 查 H2)
      -> JwtService 签发瘦 token (sub/iat/exp, ~150B)

请求: Bearer token -> JwtAuthenticationFilter
      ├─ 验签/过期检查 (本地, 无状态)
      ├─ AuthorityResolver.resolve(username)
      │    ├─ Redis GET auth:authorities:{username} ─命中─> 重建 Authentication
      │    ├─ 未命中 ─> H2 查 User/Role/Permission ─> SET 回填 (TTL 10m)
      │    └─ Redis 挂 ─> 降级直查 H2 (不缓存)
      └─ 路径级 hasRole / 方法级 @PreAuthorize hasAuthority
           失败: 401 EntryPoint / 403 AccessDeniedHandler (统一 JSON)

权限变更: UPDATE DB -> DELETE /api/v1/admin/authority-cache/{username}
          -> 下一请求重新装载 -> 即时生效
```

## 技术栈

| 组件 | 版本 | 说明 |
|---|---|---|
| Spring Boot | 4.0.0 | parent: spring-boot-starter-parent |
| Java | 21 | |
| Spring Security | 7.0.x | Lambda DSL、无状态 |
| Spring Data Redis | 4.0.x | 权限缓存（cache-aside + 降级） |
| JJWT | 0.13.0 | 新链式 API（`subject()/expiration()/parseSignedClaims()`） |
| Jackson | 3.x | Boot 4 默认（`tools.jackson` 包名） |
| 数据库 | H2 内存库 | `data.sql` 初始化种子数据 |

## 种子账号

| 账号 | 密码 | 角色 | 权限 |
|---|---|---|---|
| `admin` | `admin123` | ROLE_ADMIN | user:read, user:write, admin:read |
| `user` | `user123` | ROLE_USER | user:read |

## 启动与验证

```bash
cd lin-spring-security-jwt

# 1. 先启动 Redis（任选其一）
docker run -d --name redis -p 6379:6379 redis:7
# 或 Windows 本机 Memurai / WSL redis-server

# 2. 运行（Redis 未启动也能跑：自动降级直查 H2）
.\mvnw.cmd spring-boot:run

# 3. 测试（无需 Redis：test profile 使用 InMemoryAuthorityResolver）
.\mvnw.cmd test
```

- H2 控制台：http://localhost:8080/h2-console（JDBC URL `jdbc:h2:mem:securityjwt`，用户 `sa`，空密码）
- 手动测试：IDE 中打开 `api.http` 逐条执行
- Redis 中的权限缓存：`KEYS auth:authorities:*` / `GET auth:authorities:admin`（JSON 数组）

## 接口清单

| 方法 | 路径 | 授权 | 说明 |
|---|---|---|---|
| POST | `/api/v1/auth/login` | 公开 | 登录获取瘦 JWT（登录时顺带清缓存） |
| GET | `/api/v1/users/me` | 认证 | 当前用户信息 |
| GET | `/api/v1/users` | `user:read` | 用户列表（方法级） |
| DELETE | `/api/v1/users/{id}` | `user:write` | 删除用户（方法级，删后清缓存） |
| GET | `/api/v1/admin/stats` | `ROLE_ADMIN` | 管理端数据（路径级） |
| GET | `/api/v1/admin/audit` | `admin:read` | 审计数据（方法级） |
| DELETE | `/api/v1/admin/authority-cache/{username}` | `admin:read` | **清除权限缓存（权限变更即时生效的钥匙）** |

### curl 快速验证

```bash
# 1. 登录拿 token（注意响应里 token 很短，且不含权限）
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .accessToken)

# 2. 携带 token 访问（权限来自 Redis/H2，不在 token 里）
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/admin/stats

# 3. 权限回收演示：改库后 evict，旧 token 立即失去权限
#    （在 H2 控制台执行: DELETE FROM user_roles WHERE user_id=2 等效于收回 user 角色）
curl -X DELETE -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/v1/admin/authority-cache/user
```

## 代码结构（关键类）

```
security/
├── JwtService.java              # 瘦 token 签发/验签（只有 sub/iat/exp）
├── JwtAuthenticationFilter.java # 验签 + AuthorityResolver 装配权限
├── AuthorityResolver.java       # 缓存-旁路 权限解析接口 (resolve/evict)
├── RedisAuthorityResolver.java  # Redis 实现（生产, @Profile("!test"), 含 DB 降级）
├── InMemoryAuthorityResolver.java # 内存实现（测试, @Profile("test"), 免 Redis）
├── CustomUserDetailsService.java  # DB -> UserDetails（缓存 miss 时被复用）
├── JwtAuthenticationEntryPoint.java # 401 JSON
└── JwtAccessDeniedHandler.java     # 403 JSON
config/
├── JwtProperties.java           # app.jwt.*
├── AuthorityCacheProperties.java # app.authority-cache.ttl (默认 10m)
└── SecurityConfig.java          # 无状态过滤链 + 路径级授权 + CORS
```

## 配置项（application.yml）

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 500ms         # 快速触发降级
      connect-timeout: 500ms
app:
  jwt:
    secret: "..."            # HS256 密钥 >= 32 字符；生产用 APP_JWT_SECRET 环境变量
    expiration-ms: 3600000
  authority-cache:
    ttl: 10m                 # 权限缓存 TTL；越短权限传播越快、Redis 压力越大
```

## 生产化注意事项

1. **密钥管理**：secret 用环境变量/密钥服务注入，勿提交仓库
2. **权限变更钩子**：任何修改角色的管理操作（含直接改库）后必须调用 evict，或接受 TTL 窗口内的延迟
3. **缓存穿透**：不存在的用户名每次都会打到 DB（UsernameNotFoundException 不缓存）；高频攻击场景可缓存短 TTL 空值
4. **降级策略**：Redis 故障时本 demo 选择降级查库（可用性优先）；金融类系统可改为 fail-closed（拒绝请求）
5. **集群部署**：多实例共享 Redis 天然一致；若改用本地缓存（Caffeine）则 evict 需广播（Redis pub/sub）
6. **无刷新令牌**：按需求未实现 Refresh Token / 登出黑名单（jti + Redis SETNX 可扩展）
7. **日志安全**：不打印密码与完整 token；Redis 降级仅记录原因
