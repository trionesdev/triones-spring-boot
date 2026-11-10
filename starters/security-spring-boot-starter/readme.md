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
- 创建 `JwtFacade` 和名为 `securityWebFilterChain` 的 `SecurityFilterChain`，安装 `JwtAuthenticationFilter`。
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
| `family` | `JWSAlgorithm.Family.RSA` | 签名算法族，传入底层 `JwtConfig` |
| `algorithm` | `RS256` | 签名算法，传入底层 `JwtConfig` |
| `secret` | `secret1234567890qwertyuiopasdfghg` | 已声明，但当前自动配置未将其传入 `JwtConfig`，设置此项不会配置签名密钥 |
| `expiration` | `1800` | 令牌有效期，单位为秒 |

当前 `jwtFacade()` 将 `family`、`algorithm` 和 `expiration` 交给 `JwtConfig`，再创建 `JwtFacade`。密钥生成、加载和验签的具体行为由底层 JWT 组件决定，本 starter 没有显式配置密钥。

**固定密钥与多实例部署**：不能仅通过设置 `secret` 保证重启后或跨实例验签。需要先在 JWT 组件中配置持久化 RSA 密钥，或接入共享 HMAC 密钥，并将对应配置传入 `JwtConfig`。当前自动配置尚未接通 `secret`，因此这里不提供可直接启用 HS256 的配置示例。

`family` 和 `algorithm` 的类型是 Nimbus 的对象类型；通过 YAML 配置时，需确认应用的 Spring 配置绑定支持这些类型。Java 默认值不等同于可直接绑定的 YAML 字符串。

### 资源服务器（前缀 `triones.security.resource-server`）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `false` | 传入底层 `ResourceServerConfig`，控制行为由认证过滤器实现；不控制安全链 Bean 的创建 |
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
      enabled: true
      jwt:
        issuer-uri: http://localhost:9000
        token-uri: http://localhost:9000/oauth2/token
        jwk-set-uri: http://localhost:9000/oauth2/jwks
```

以上为授权服务器使用默认路径时的配置示例，本 starter 不会自动填充这些地址。实际地址应以认证服务的配置为准。

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
- **重启或多实例部署后令牌无法验证**：检查底层 JWT 组件是否加载了相同的持久化密钥；当前 `secret` 属性未接入密钥配置。
- **设置 `resource-server.enabled: false` 后仍启用安全链**：该属性会传入认证过滤器，但不会关闭 Spring Security 或阻止安全链创建。
