# Security OAuth2 Spring Boot Starter

`oauth2` 认证模式下的资源服务器：用 Spring Security OAuth2 Resource Server 校验 `Authorization: Bearer` 中的 JWT access token，并把令牌解析为 Triones `Actor` 写入 `ActorContext`。放行与授权规则沿用 `security-spring-boot-starter` 的 `triones.security.*`。

## 引入依赖

```xml
<dependency>
    <groupId>com.trionesdev.springboot</groupId>
    <artifactId>security-oauth2-spring-boot-starter</artifactId>
</dependency>
```

版本由 `com.trionesdev.springboot:spring-boot-dependencies` 统一管理。会同时引入 `security-spring-boot-starter`。容器中需要 `ActorContext` Bean（Triones `spring-boot-starter` 提供）。

## 开启

```yaml
triones:
  security:
    authorization:
      mode: oauth2          # 默认 jwt；为 oauth2 时由本 starter 创建安全链
```

`mode: oauth2` 时 `security-spring-boot-starter` 的 JWT 安全链与 `JwtTokenIssuer` 不再创建，改由本 starter 的 `securityWebFilterChain` 处理请求。

## 令牌校验密钥（`JwtDecoder`）

安全链使用容器中的 `JwtDecoder`：

| 部署方式 | `JwtDecoder` 来源 |
| --- | --- |
| 与授权服务器同一应用（嵌入式） | `oauth2-authorization-server-spring-boot-starter` 基于其 `JWKSource` 注册，无需配置 |
| 独立的业务服务 | Spring Boot 资源服务器自动配置，配置授权服务器地址即可 |

独立业务服务：

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.example.com          # 与授权服务器签发的 iss 一致
          # 或直接指定 JWKS 地址：
          # jwk-set-uri: https://auth.example.com/oauth2/jwks
```

## 安全链行为

- 名为 `securityWebFilterChain`，`@Order(LOWEST_PRECEDENCE)`，排在授权服务器端点链之后。
- 无状态会话；`triones.security.csrf`、`anonymous`、`default-ignored-matchers`、`ignored-matchers`、`rules` 的含义与 security starter 相同（见其 readme）。
- **放行路径不解析令牌**：`default-ignored-matchers` + `ignored-matchers` 中的路径即使带了过期或无效的令牌也不会返回 401，避免登录接口被旧令牌拦截。`rules` 中的 `PERMIT_ALL` 路径不在此列，带无效令牌仍会 401。
- 未认证或令牌无效时返回 `JwtAuthenticationEntryPoint` 的 401 JSON。

## 令牌到 `Actor` 的转换

| 令牌 | 结果 |
| --- | --- |
| 平台登录令牌（含 `ACTOR_*` 声明，由嵌入式授权服务器签发） | 交给 `JwtActorResolver` 解析，与 `jwt` 模式下解析出的 `Actor` 一致 |
| 第三方 / `client_credentials` 令牌（没有 `ACTOR_*` 声明） | `actorId` 取 `sub` |

- 令牌中的 `client_id` 写入 `actor.attributes`。
- 权限 = `ActorAuthoritiesResolver` 的结果 + 标准 scope 权限（`SCOPE_xxx`），可用于 `rules` 的 `HAS_AUTHORITY`：

  ```yaml
  triones:
    security:
      rules:
        - matchers: [/api/orders/**]
          method: GET
          action: HAS_AUTHORITY
          authorities: [SCOPE_order.read]
  ```
- 认证后的 `Actor`（放行的请求为只含 traceId 等的空 `Actor`）在请求期间写入 `ActorContext`，请求结束清理。

## 扩展

| Bean | 说明 |
| --- | --- |
| `JwtActorResolver` | 自定义 `ACTOR_*` 声明的解析，默认 `DefaultJwtActorResolver` |
| `ActorAuthoritiesResolver` | 自定义权限解析，默认 `DefaultActorAuthoritiesResolver` |
| `JwtDecoder` | 自定义令牌校验（例如额外校验 audience） |

## 注意

- `security-spring-boot-starter` 的 `JwtFacade` 在 `oauth2` 模式下仍会创建；未配置 `triones.security.jwt.key.*` 时启动日志会出现临时密钥的 WARN，不影响 OAuth2 令牌校验。
- 本 starter 只负责校验令牌；签发令牌需要 `oauth2-authorization-server-spring-boot-starter`（或外部授权服务器）。
