# Security Spring Boot Starter

基于 Spring Security 和 Triones JWT 组件，为 Servlet Web 应用提供无状态 JWT 认证与接口放行配置。当前项目使用 Java 17、Spring Boot 3.3.0。

## 引入依赖

```xml
<dependency>
    <groupId>com.trionesdev.springboot</groupId>
    <artifactId>security-spring-boot-starter</artifactId>
    <version>3.0.3-SNAPSHOT</version>
</dependency>
```

版本应与项目使用的 Triones Spring Boot 依赖保持一致。应用还需要提供 `ActorContext` Bean；可接入 Triones 基础 starter 的上下文自动配置，或自行注册。

## 快速配置

在 `application.yml` 中声明允许未认证访问的接口：

```yaml
triones:
  security:
    exclude-post-matchers:
      - /api/auth/login
    exclude-get-matchers:
      - /api/public/**
    jwt:
      expiration: 1800
```

上述配置允许 POST 登录接口和 GET 公共接口访问，其余请求需要通过认证。登录接口、用户凭据校验和令牌签发业务需由应用实现；starter 不自动提供登录端点。

## 默认行为

- 自动注册 `SecurityConfiguration` 和 `JwtSecurityConfiguration`，启用 Web Security。
- 创建 `JwtTokenFacade` 和名为 `securityWebFilterChain` 的 `SecurityFilterChain`，安装 `JwtAuthenticationFilter`。
- 使用 `STATELESS` 会话策略，关闭 CSRF 和匿名认证。
- 放行匹配配置的请求，其余请求要求 `authenticated()`；认证入口使用 `JwtAuthenticationEntryPoint`。

以下路径默认通过 `permitAll()` 放行：

```text
/favicon.ico
/v3/api-docs/**
/v2/api-docs
/webjars/**
/swagger-resources/**
/swagger-ui/**
/swagger-ui.html
/actuator/**
/websocket/**
```

默认路径与 `ignore-matchers` 合并，配置不能移除内置放行项。部署前应确认 Actuator、Swagger 和 WebSocket 端点的访问范围。

## 配置项

### 请求匹配（前缀 `triones.security`）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `exclude-matchers` | `[]` | 所有 HTTP 方法均放行的路径 |
| `exclude-get-matchers` | `[]` | 仅 GET 请求放行的路径 |
| `exclude-post-matchers` | `[]` | 仅 POST 请求放行的路径 |
| `exclude-put-matchers` | `[]` | 仅 PUT 请求放行的路径 |
| `exclude-delete-matchers` | `[]` | 仅 DELETE 请求放行的路径 |
| `ignore-matchers` | `[]` | 追加到内置放行列表的路径 |

这些配置均调用 `requestMatchers(...).permitAll()`，请求仍经过安全过滤器链。路径示例：`/api/public/**`。

### JWT（前缀 `triones.security.jwt`）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `secret` | `secret1234567890qwertyuiopasdfghg` | 当前实现用于 RSA JWK 的 `keyID`，并非签名密钥材料；为空时生成随机 UUID |
| `expiration` | `1800` | 令牌有效期，单位为秒 |

当前实现每次创建 `JwtTokenFacade` 都会生成新的 2048 位 RSA 密钥。设置相同的 `secret` 不会复用同一密钥，因此不能据此实现跨实例验签或保证重启前签发的令牌继续有效。需要这些能力时，应调整密钥管理方案。

### 资源服务器（前缀 `triones.security.resource-server`）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `false` | 已定义的属性；当前自动配置未读取此值，不能作为启停开关 |
| `jwt.issuer-uri` | 未设置 | 传入底层 JWT 配置的签发者 URI |
| `jwt.token-uri` | 未设置 | 传入底层 JWT 配置的令牌 URI |
| `jwt.jwk-set-uri` | 未设置 | 传入底层 JWT 配置的 JWK 集合 URI |

参考 [Spring Authorization Server 默认端点](https://docs.spring.io/spring-authorization-server/reference/configuration-model.html)，假设授权服务器地址为 `http://localhost:9000`，可参考以下地址：

| 配置项 | 参考地址 | 说明 |
| --- | --- | --- |
| `jwt.issuer-uri` | `http://localhost:9000` | 应与授权服务器配置的 issuer 完全一致；issuer 没有固定默认地址 |
| `jwt.token-uri` | `http://localhost:9000/oauth2/token` | 默认令牌端点 |
| `jwt.jwk-set-uri` | `http://localhost:9000/oauth2/jwks` | 默认 JWK 集合端点，服务端需注册 `JWKSource` |

```yaml
triones:
  security:
    resource-server:
      jwt:
        issuer-uri: http://localhost:9000
        token-uri: http://localhost:9000/oauth2/token
        jwk-set-uri: http://localhost:9000/oauth2/jwks
```

以上为授权服务器使用默认路径时的配置示例，本 starter 不会自动填充这些地址。若验证服务采用 OAuth2 令牌内省协议，其默认端点为 `http://localhost:9000/oauth2/introspect`；将其用于 `verification-uri` 前，需确认底层验证实现支持该协议及客户端认证要求。

这些 URI 会封装为 `ResourceServerConfig` 交给 `JwtAuthenticationFilter`，具体处理逻辑由依赖 `com.trionesdev.commons:spring-security` 实现，应按所接入的认证服务配置。

## 扩展与集成

应用可按需提供以下 Bean，自动配置会通过 `ObjectProvider` 获取并传入 JWT 认证过滤器：

| 类型 | 用途 |
| --- | --- |
| `com.trionesdev.spring.security.jwt.JwtActorResolver` | 扩展 JWT 中的 Actor 解析 |
| `com.trionesdev.spring.security.ActorAuthoritiesResolver` | 扩展 Actor 权限解析 |

当前自动配置没有使用 `@ConditionalOnMissingBean` 进行默认 Bean 退让。需要完全自定义安全链或令牌组件时，应显式排除相关自动配置，并自行提供对应配置，避免 Bean 冲突或多个安全链匹配范围重叠。

## 排查问题

- **缺少 `ActorContext` Bean**：检查上下文组件是否已注册到 Spring 容器。
- **公开接口仍要求认证**：检查路径及 HTTP 方法是否匹配；例如 `exclude-get-matchers` 不会放行 POST 请求。
- **重启或多实例部署后令牌无法验证**：检查是否受每次启动生成独立 RSA 密钥的行为影响。
- **设置 `resource-server.enabled: false` 后仍启用安全链**：当前实现没有使用该属性控制自动配置。
