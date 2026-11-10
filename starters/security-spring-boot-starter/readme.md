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

默认仅放行 `/favicon.ico`。Swagger、Actuator 和 WebSocket 路径需要应用显式配置。

`default-matchers` 可替换默认列表，`ignore-matchers` 用于追加：

```yaml
triones:
  security:
    default-matchers: []
    ignore-matchers:
      - /api/public/**
```

## 配置项

### 请求匹配（前缀 `triones.security`）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `exclude-matchers` | `[]` | 所有 HTTP 方法均放行的路径 |
| `exclude-get-matchers` | `[]` | 仅 GET 请求放行的路径 |
| `exclude-post-matchers` | `[]` | 仅 POST 请求放行的路径 |
| `exclude-put-matchers` | `[]` | 仅 PUT 请求放行的路径 |
| `exclude-delete-matchers` | `[]` | 仅 DELETE 请求放行的路径 |
| `default-matchers` | `["/favicon.ico"]` | 默认放行列表，配置后整体替换；设为空列表可移除默认项 |
| `ignore-matchers` | `[]` | 追加到默认放行列表的路径 |

这些配置均调用 `requestMatchers(...).permitAll()`，请求仍经过安全过滤器链。路径示例：`/api/public/**`。

### JWT（前缀 `triones.security.jwt`）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `algorithm` | `RS256` | 签名算法，传入底层 `JwtConfig` |
| `secret` | 未设置 | 传入底层 `JwtConfig`，具体密钥处理由 JWT 组件决定 |
| `expiration` | `1800` | 令牌有效期，单位为秒 |

当前 `jwtFacade()` 将 `algorithm`、`secret` 和 `expiration` 交给 `JwtConfig`，再创建 `JwtFacade`。密钥生成、加载和验签的具体行为由底层 JWT 组件决定，本 starter 没有显式配置密钥。

**固定密钥与多实例部署**：RSA/EC 默认生成临时密钥，需要通过底层 `JwtFacade` 的 JWK 构造参数传入持久化密钥。HMAC 根据 `secret` 创建密钥；各实例应使用相同算法和相同 secret。

算法族由 `algorithm` 自动推导，不需要配置 `family`。`algorithm` 的类型是 Nimbus 的对象类型；通过 YAML 配置时，需确认应用的 Spring 配置绑定支持这些类型。Java 默认值不等同于可直接绑定的 YAML 字符串。

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

`JwtFacadeConfiguration` 独立创建 JWT 组件，不依赖 `ActorContext`，可用于非 Web 应用。应用提供 `JwtFacade` Bean 时默认实现退让。

安全链仅在 Servlet Web 环境且相关类存在时配置；应用提供任意 `SecurityFilterChain` Bean 时，默认安全链退让。默认安全链需要应用提供 `ActorContext`。

默认 JWT 配置在启动时检查算法和有效期。HMAC 必须显式提供 secret：HS256 为 32、HS384 为 48、HS512 为 64 个 UTF-8 字节。RSA/EC 的密钥兼容性由 `JwtFacade` 构造时检查。自定义 `JwtFacade` 时，配置校验由应用负责。

## 排查问题

- **缺少 `ActorContext` Bean**：检查上下文组件是否已注册到 Spring 容器。
- **公开接口仍要求认证**：检查路径及 HTTP 方法是否匹配；例如 `exclude-get-matchers` 不会放行 POST 请求。
- **重启或多实例部署后令牌无法验证**：检查底层 JWT 组件是否加载了相同的持久化密钥；RSA/EC 默认生成临时密钥，HMAC 则需保持算法和 secret 一致。
- **设置 `resource-server.enabled: false` 后仍启用安全链**：该属性会传入认证过滤器，但不会关闭 Spring Security 或阻止安全链创建。
