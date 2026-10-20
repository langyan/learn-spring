# lin-spring-shiro-jwt

Spring Boot 4 + **Apache Shiro 3** + JWT 认证授权 Demo：与 `lin-spring-security-jwt` 完全同构的业务实现（瘦 Token + Redis 权限缓存 + RBAC），仅把安全框架从 Spring Security 换成 Shiro，用于两套框架的对照学习。

## 与 Spring Security 版的映射关系

| Spring Security 版 | Shiro 版（本模块） |
|---|---|
| `SecurityFilterChain` 过滤链 | `ShiroFilterFactoryBean` chain map（anon/jwt） |
| `JwtAuthenticationFilter`（OncePerRequestFilter） | `JwtFilter`（extends `AuthenticatingFilter`） |
| `UserDetailsService` + `DaoAuthenticationProvider` | `JwtRealm`（extends `AuthorizingRealm`）+ `UserAuthService` |
| `AuthenticationManager` | `DefaultWebSecurityManager` |
| `SessionCreationPolicy.STATELESS` | `SessionStorageEvaluator(sessionStorageEnabled=false)` |
| `hasRole("ADMIN")` 路径规则 | `@RequiresRoles("ADMIN")` 类级注解（AOP） |
| `@PreAuthorize("hasAuthority('user:read')")` | `@RequiresPermissions("user:read")`（通配符权限天然兼容） |
| `AuthenticationEntryPoint`（401 JSON） | `JwtFilter` 直接写 401 JSON |
| `AccessDeniedHandler`（403 JSON） | `UnauthorizedException` → `GlobalExceptionHandler` |
| `GrantedAuthority` 集合 | `SimpleAuthorizationInfo`（roles + stringPermissions） |

## 架构（与 Security 版一致：瘦 Token + Redis）

```
登录: 账号密码 -> UserAuthService (BCrypt 直查 H2)
      -> JwtUtil 签发瘦 token (sub/iat/exp, ~150B)

请求: Bearer token -> JwtFilter (Shiro 过滤链 "jwt")
      ├─ JwtRealm.doGetAuthenticationInfo: 本地验签 (无状态, 不查库)
      ├─ JwtRealm.doGetAuthorizationInfo: AuthorityResolver 取权限
      │    ├─ Redis GET auth:authorities:{username} ─命中─> 直接返回
      │    ├─ 未命中 ─> H2 查库 ─> SET 回填 (TTL 10m)
      │    └─ Redis 挂 ─> 降级直查 H2
      └─ @RequiresRoles / @RequiresPermissions 注解授权
           失败: 401 (JwtFilter JSON) / 403 (GlobalExceptionHandler JSON)

权限变更: UPDATE DB -> DELETE /api/v1/admin/authority-cache/{username}
          -> 下一请求重新装载 -> 即时生效
```

权限字符串 `user:read` 恰好就是 Shiro 的 WildcardPermission 语法（`domain:action`），RBAC 种子数据零改动直接可用；角色以 `ROLE_ADMIN` 形式存库，Realm 装配时去掉 `ROLE_` 前缀（**Shiro 角色比较区分大小写**）。

## 踩坑记录（Boot 4 + Shiro 集成要点）

1. **Shiro 3.x 原生 Jakarta，无需 classifier**：直接依赖 `shiro-core/shiro-web/shiro-spring` 默认 artifact 即是 `jakarta.servlet` 命名空间（Java 17 基线，模块拆分为 shiro-lang/shiro-cache/shiro-crypto-* 等）。若仍在用 Shiro 2.x，则必须给每个依赖加 `<classifier>jakarta</classifier>`，否则默认 artifact 是 `javax.servlet`，编译报"无法访问 javax.servlet.Filter"。
2. **Boot 4 无 `spring-boot-starter-aop`**：注解授权所需的 spring-aop 已随 spring-context 传递提供。
3. **`AuthorizationAttributeSourceAdvisor` 必须 `@Lazy` 注入 SecurityManager**：Advisor 在 BeanPostProcessor 注册阶段被提前实例化，会连带拉起 Realm → AuthorityResolver → JPA Repository，此时 `entityManagerFactory` 尚未就绪导致启动失败。
4. **`setFilters()` 必须传可变 Map**：`ShiroFilterFactoryBean` 实现了 `BeanPostProcessor`，会自动收集容器中的 Filter bean 放入该 map——`Map.of()` 不可变，直接 `UnsupportedOperationException`。
5. **`@RequiresRoles` 大小写敏感**：`hasRole("ADMIN")` 在 Security 里自动加 `ROLE_` 前缀比较；Shiro 存什么比什么，`"admin" ≠ "ADMIN"`。
6. **BCrypt 不随 Shiro 提供**：仅引入 `spring-security-crypto`（无 Security 本体依赖），登录时手动 `matches()` 校验。
7. **data.sql 与 Hibernate DDL 顺序**：`defer-datasource-initialization: true` 必须开启。

## 种子账号（与 Security 版相同）

| 账号 | 密码 | 角色 | 权限 |
|---|---|---|---|
| `admin` | `admin123` | ROLE_ADMIN | user:read, user:write, admin:read |
| `user` | `user123` | ROLE_USER | user:read |

## 启动与验证

```bash
cd lin-spring-shiro-jwt

# 1. Redis（可选：未启动自动降级直查 H2）
docker run -d --name redis -p 6379:6379 redis:7

# 2. 运行
.\mvnw.cmd spring-boot:run

# 3. 测试（无需 Redis：test profile 使用 InMemoryAuthorityResolver）
.\mvnw.cmd test
```

- H2 控制台：http://localhost:8080/h2-console（JDBC URL `jdbc:h2:mem:shirojwt`，用户 `sa`）
- 手动测试：`api.http` 15 个请求覆盖成功/401/403/权限回收场景

## 接口清单

| 方法 | 路径 | 授权 | 说明 |
|---|---|---|---|
| POST | `/api/v1/auth/login` | anon | 登录获取瘦 JWT |
| GET | `/api/v1/users/me` | jwt | 当前用户信息 |
| GET | `/api/v1/users` | `@RequiresPermissions("user:read")` | 用户列表 |
| DELETE | `/api/v1/users/{id}` | `@RequiresPermissions("user:write")` | 删除用户（清缓存） |
| GET | `/api/v1/admin/stats` | `@RequiresRoles("ADMIN")` | 管理端数据 |
| GET | `/api/v1/admin/audit` | `@RequiresPermissions("admin:read")` | 审计数据 |
| DELETE | `/api/v1/admin/authority-cache/{username}` | `@RequiresPermissions("admin:read")` | 清除权限缓存 |

## 代码结构

```
auth/
├── JwtUtil.java               # 瘦 token 签发/验签（框架无关）
├── JwtToken.java              # org.apache.shiro.authc.AuthenticationToken 适配
├── JwtRealm.java              # 认证(验签) + 授权(缓存取权限拆 roles/perms)
├── JwtFilter.java             # Bearer 解析 + executeLogin + 401 JSON
├── AuthorityResolver.java     # 权限缓存接口 (Set<String>)
├── RedisAuthorityResolver.java   # Redis 实现（!test, DB 降级）
├── InMemoryAuthorityResolver.java # 内存实现（test）
└── UserAuthService.java       # BCrypt 登录校验 + 权限装配（≈UserDetailsService）
config/
└── ShiroConfig.java           # Realm/SecurityManager/过滤链/无状态 session/注解授权
```

## 测试覆盖（14/14）

与 Security 版同套测试：登录/瘦 token 断言/401×3/403×2/权限回收三段式（改库→缓存陈旧 200→evict→403）/清缓存端点。
