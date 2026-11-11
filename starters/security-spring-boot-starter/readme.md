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
| `key.key-id` | JWK thumbprint | JWT 头中的 `kid`；默认按密钥内容计算，同一密钥在所有实例上一致 |
| `key.jwk-location` | 未设置 | 含私钥的 JWK JSON 文件（RSA / EC / oct） |
| `key.private-key-location` | 未设置 | 未加密的 PKCS#8 PEM 私钥（`-----BEGIN PRIVATE KEY-----`） |
| `key.public-key-location` | 未设置 | X.509 PEM 公钥（`-----BEGIN PUBLIC KEY-----`）；RSA 可省略（由私钥推导），EC 必填 |
| `key.key-store.location` | 未设置 | keystore 文件 |
| `key.key-store.type` | `PKCS12` | `PKCS12` 或 `JKS` |
| `key.key-store.password` | 未设置 | keystore 密码 |
| `key.key-store.alias` | 未设置 | 密钥别名；keystore 中只有一个私钥条目时可省略 |
| `key.key-store.key-password` | 同 `password` | 私钥密码 |
| `jwks.enabled` | `false` | 是否开启 JWKS 端点（仅 RSA/EC） |
| `jwks.path` | `/auth/jwks` | JWKS 端点路径（Spring Authorization Server 默认为 `/oauth2/jwks`） |
| `jwks.cache-max-age` | `5m` | 响应 `Cache-Control: public, max-age=…`；`0` 时返回 `no-store` |
| `key.verification-keys[n].*` | `[]` | 仅用于校验的旧密钥（密钥轮换），每项支持与上面相同的 `key-id`、`jwk-location`、`private-key-location`、`public-key-location`、`key-store.*`；RSA/EC 只需公钥 |

`*-location` 支持 Spring `Resource` 写法：`classpath:jwt/private.pem`、`file:/etc/app/jwt.p12`。

**固定密钥与多实例部署**：RSA/EC 未配置 `key.*` 时，每次启动随机生成临时密钥（启动时输出 WARN），重启后旧 token 失效，多实例之间也无法互相验证。生产环境请配置持久化密钥，所有实例加载同一份。HMAC 根据 `secret` 创建密钥，各实例使用相同算法和相同 secret 即可。

密钥来源 `jwk-location`、`private-key-location`、`key-store.location` 只能配置其中一个。启动时会校验：资源是否存在、PEM 格式、密钥类型与 `algorithm` 是否匹配（如 `ES256` 配了 RSA 私钥会启动失败）。HMAC 算法不接受 PEM / keystore，只能用 `secret` 或 oct 类型的 JWK。

**方式一：PEM 私钥**

```bash
# 生成 RSA 私钥（PKCS#8）
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
# 已有 PKCS#1（BEGIN RSA PRIVATE KEY）私钥时转换为 PKCS#8
openssl pkcs8 -topk8 -nocrypt -in rsa-key.pem -out jwt-private.pem

# EC（ES256）需要同时提供公钥
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out jwt-ec-private.pem
openssl pkey -in jwt-ec-private.pem -pubout -out jwt-ec-public.pem
```

```yaml
triones:
  security:
    jwt:
      algorithm: RS256
      key:
        private-key-location: file:/etc/app/jwt-private.pem
```

**方式二：keystore**

```bash
keytool -genkeypair -keyalg RSA -keysize 2048 -alias jwt -dname "CN=jwt" -validity 3650 \
  -storetype PKCS12 -keystore jwt.p12 -storepass changeit
```

```yaml
triones:
  security:
    jwt:
      algorithm: RS256
      key:
        key-store:
          location: file:/etc/app/jwt.p12
          password: ${JWT_KEYSTORE_PASSWORD}
          alias: jwt
```

**方式三：JWK 文件**

```yaml
triones:
  security:
    jwt:
      algorithm: RS256
      key:
        jwk-location: file:/etc/app/jwt.jwk.json
```

> 私钥不要打包进 jar 或提交到代码仓库，建议通过挂载文件（K8s Secret、配置中心落盘）配合 `file:` 路径加载。

#### 密钥轮换

`JwtFacade` 始终只用 `key` 签发，`key.verification-keys` 中的密钥只用于校验。校验时按 JWT 头的 `kid` 选择密钥，`kid` 缺失或未命中时依次尝试所有已配置的密钥（签名必须能被其中之一验证通过，未配置的密钥签发的 token 一律拒绝）。

```yaml
triones:
  security:
    jwt:
      algorithm: RS256
      expiration: 1800
      key:
        private-key-location: file:/etc/app/jwt-2026.pem        # 新密钥：签发 + 校验
        verification-keys:
          - public-key-location: file:/etc/app/jwt-2025.pub.pem  # 旧密钥：只校验
```

多实例平滑轮换步骤：

1. **预发布新公钥**：所有实例保持旧私钥签发，把新公钥加入 `verification-keys` 并滚动发布。此时任何实例都能校验新密钥签发的 token。
2. **切换签名密钥**：`key` 换成新私钥，旧公钥移入 `verification-keys`，滚动发布。发布过程中新旧实例签发的 token 可互相校验。
3. **移除旧密钥**：等待至少 `expiration` 秒（旧 token 全部过期）后，从 `verification-keys` 删除旧密钥。

如果可以接受发布期间少量请求校验失败，可跳过第 1 步。

注意事项：

- `kid` 默认是 JWK thumbprint，同一把密钥在签发端和校验端算出的值一致，无需额外配置；若签发时设置了自定义 `key-id`，校验端也要配置相同的 `key-id`（或依赖 `kid` 未命中时的回退尝试）。
- 配置了 `verification-keys` 时必须同时配置持久化签名密钥，否则启动失败。
- `JwtFacade.getPublicJwkSet()` 返回签名公钥（第一个）和所有验证公钥，对外提供 JWKS 端点时，远程校验方在轮换期间也能同时认可新旧 token。
- HMAC 轮换：`secret` 换成新值，旧密钥以 oct 类型 JWK 文件放入 `verification-keys[n].jwk-location`；HMAC 不对外暴露 JWKS。

#### JWKS 端点

签发 token 的服务开启 JWKS 端点后，其他服务无需持有任何密钥，配置 `resource-server.jwt.jwk-set-uri` 即可远程校验：

```yaml
# 签发方（登录服务）
triones:
  security:
    jwt:
      algorithm: RS256
      key:
        private-key-location: file:/etc/app/jwt-private.pem
      jwks:
        enabled: true
        path: /auth/jwks
        cache-max-age: 5m
```

```yaml
# 校验方（其他服务）
triones:
  security:
    resource-server:
      enabled: true
      jwt:
        jwk-set-uri: http://auth-service/auth/jwks
```

```bash
curl http://localhost:8080/auth/jwks
# {"keys":[{"kty":"RSA","e":"AQAB","kid":"...","n":"..."}]}
```

- 响应为标准 JWK Set（RFC 7517），内容来自 `JwtFacade.getPublicJwkSet()`：签名公钥在前，随后是 `key.verification-keys` 的公钥，**不含任何私钥字段**。密钥轮换期间远程校验方可同时认可新旧 token。
- 端点由 `JwksEndpointConfigurer` 挂在 **Spring Security 过滤器链内**（与 Spring Authorization Server 的 `NimbusJwkSetEndpointFilter` 做法一致），位于认证/授权过滤器之前并直接写出响应，因此**无需**加入 `exclude-*-matchers`。链中的安全响应头（如 `X-Content-Type-Options`）、CORS 配置照常生效；不依赖 Spring MVC。
- `jwks.*` 配置只作用于 starter 默认安全链。应用自定义 `SecurityFilterChain` 时需手动接入 `com.trionesdev.spring.security.authorization.server.jwks.JwksEndpointConfigurer`（位于 `spring-authorization-server` 组件中，不依赖 starter 配置）。未传入时 `JwtFacade` 从容器获取，路径默认 `/auth/jwks`，缓存默认 5 分钟；要沿用 `triones.security.jwt.jwks.*` 配置，注入 `JwtSecurityProperties` 后传入：

  ```java
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, JwtSecurityProperties properties) throws Exception {
      JwtSecurityProperties.Jwks jwks = properties.getJwks();
      http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
          // 链式调用时需显式写出泛型参数 <HttpSecurity>
          .with(new JwksEndpointConfigurer<HttpSecurity>()
                  .path(jwks.getPath())
                  .cacheMaxAge(jwks.getCacheMaxAge()), Customizer.withDefaults());
      return http.build();
  }
  ```
- 只响应 `GET` / `HEAD`，其他方法返回 `405`；路径基于 context-path 之后的部分匹配。
- 密钥在启动时序列化一次，更换密钥需重启。远程校验方会缓存 JWKS（nimbus 默认缓存数分钟，并在遇到未知 `kid` 时刷新），因此轮换时建议先预发布新公钥（见上文步骤 1）。
- HMAC 算法开启 `jwks.enabled` 会启动失败，对称密钥不能公开。
- 未配置持久化密钥时端点也能工作，但每个实例返回不同的临时公钥，多实例下远程校验会随机失败，生产环境务必配置 `key.*`。

算法族由 `algorithm` 自动推导，不需要配置 `family`。`algorithm` 使用 `Algorithm` 枚举，支持 YAML 绑定及 IDE 配置候选值提示：`HS256`、`HS384`、`HS512`、`RS256`、`RS384`、`RS512`、`PS256`、`PS384`、`PS512`、`ES256`、`ES384`、`ES512`。

```yaml
triones:
  security:
    jwt:
      algorithm: HS256
      secret: ${JWT_SECRET}
      expiration: 1800
```

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

### 服务间调用客户端

以 client_credentials 模式申请服务 token 的自动配置（`triones.security.auth.client.*`）已拆分到 `auth-client-spring-boot-starter`，见其 readme。

## 扩展与集成

应用可按需提供以下 Bean，自动配置会通过 `ObjectProvider` 获取并传入 JWT 认证过滤器：

| 类型 | 用途 |
| --- | --- |
| `com.trionesdev.spring.security.jwt.JwtActorResolver` | 扩展 JWT 中的 Actor 解析 |
| `com.trionesdev.spring.security.ActorAuthoritiesResolver` | 扩展 Actor 权限解析 |

`JwtFacadeConfiguration` 独立创建 JWT 组件，不依赖 `ActorContext`，可用于非 Web 应用。应用提供 `JwtFacade` Bean 时默认实现退让。

安全链仅在 Servlet Web 环境且相关类存在时配置；应用提供任意 `SecurityFilterChain` Bean 时，默认安全链退让。默认安全链需要应用提供 `ActorContext`。

默认 JWT 配置在启动时检查算法和有效期。HMAC 必须显式提供 secret：HS256 上限为 32、HS384 为 48、HS512 为 64 个 UTF-8 字节。不足时由底层 `JwtFacade` 在字节数组尾部补零（`0x00`，不是字符 `0`），超出时启动报错。RSA/EC 的密钥兼容性由 `JwtFacade` 构造时检查。自定义 `JwtFacade` 时，配置校验由应用负责。

## 排查问题

- **缺少 `ActorContext` Bean**：检查上下文组件是否已注册到 Spring 容器。
- **公开接口仍要求认证**：检查路径及 HTTP 方法是否匹配；例如 `exclude-get-matchers` 不会放行 POST 请求。
- **重启或多实例部署后令牌无法验证**：启动日志出现 `No persistent JWT signing key configured` 说明 RSA/EC 使用了临时密钥，请配置 `triones.security.jwt.key.*`；HMAC 则需保持算法和 secret 一致。
- **设置 `resource-server.enabled: false` 后仍启用安全链**：该属性会传入认证过滤器，但不会关闭 Spring Security 或阻止安全链创建。
