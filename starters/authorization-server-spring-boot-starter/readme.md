# Authorization Server Spring Boot Starter

`spring-authorization-server` 的自动配置：开箱提供 OAuth2 token 端点（`client_credentials`）与 JWKS 端点。依赖 `security-spring-boot-starter`，签名密钥沿用 `triones.security.jwt.*`（`JwtFacade`）。

## 引入依赖

```xml
<dependency>
    <groupId>com.trionesdev.springboot</groupId>
    <artifactId>authorization-server-spring-boot-starter</artifactId>
</dependency>
```

## 配置

```yaml
triones:
  security:
    jwt:
      algorithm: RS256
      key:
        private-key-location: file:/etc/app/jwt-private.pem   # 多实例 / JWKS 远程校验时必须用持久化密钥
    authorization-server:
      enabled: true                          # 默认 true
      issuer: https://auth.example.com       # 写入 iss，可选
      token:
        enabled: true                        # 默认 true
        path: /auth/token                    # 默认 /auth/token
        access-token-time-to-live: 30m       # 客户端未单独设置时的有效期，默认 30 分钟
        clients:
          - client-id: order-service
            client-secret: "{bcrypt}$2a$10$..."   # 编码后的值；开发环境可用 {noop}secret
            scopes: [order.read, order.write]
            access-token-time-to-live: 10m        # 可选
            client-authentication-methods: [client-secret-basic]  # 可选，默认 basic 与 post 都允许
      jwks:
        enabled: true                        # 默认 true；HMAC 签名时必须设为 false
        path: /auth/jwks                     # 默认 /auth/jwks
        cache-max-age: 5m                    # 0 表示 no-store
```

请求与响应格式、错误码、token claims 见 `spring-authorization-server` 模块 README。

## 自动配置内容

| Bean | 条件 | 说明 |
| --- | --- | --- |
| `registeredClientRepository` | token 端点开启，且无自定义 `RegisteredClientRepository` | 由 `token.clients` 构建 `InMemoryRegisteredClientRepository`；客户端配置不合法（缺少 id / secret 等）时启动失败 |
| `authorizationServerSecurityFilterChain` | token 或 JWKS 端点至少开启一个，且无同名 Bean | 独立的 `SecurityFilterChain`，`@Order(HIGHEST_PRECEDENCE)`，只匹配端点路径 |

- 端点链与 security starter 的默认资源服务器链（`securityWebFilterChain`）或应用自定义的链并存，其他请求不受影响；
- 端点链无状态、关闭 CSRF，端点过滤器直接写出响应，无需 `permitAll()`；
- 容器中的 `TokenClaimsCustomizer` Bean 按 `@Order` 依次执行，可加入租户、角色等 claim；
- 容器中存在唯一 `PasswordEncoder` 时用于校验 client secret，否则使用 `DelegatingPasswordEncoder`（secret 需带 `{bcrypt}` 等前缀）。

## 自定义

客户端存放在数据库时，注册自己的仓库即可：

```java
@Bean
RegisteredClientRepository registeredClientRepository(ClientMapper mapper) {
    return clientId -> mapper.findByClientId(clientId);   // 返回 RegisteredClient，不存在返回 null
}

@Bean
TokenClaimsCustomizer tenantClaims() {
    return (claims, client, scopes) -> claims.claim(ClaimsKeyConstant.ACTOR_ROLE, "SERVICE");
}
```

需要完全控制端点链时，定义名为 `authorizationServerSecurityFilterChain` 的 `SecurityFilterChain`，并按模块 README 用 `TokenEndpointConfigurer` / `JwksEndpointConfigurer` 自行组装。

> security starter 的 `triones.security.jwt.jwks.enabled` 会在默认链中再挂一个 JWKS 端点；使用本 starter 时无需开启。
