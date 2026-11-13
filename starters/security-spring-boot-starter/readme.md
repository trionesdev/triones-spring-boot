# Security Spring Boot Starter

基于 Spring Security 与 Triones JWT 组件，为 Servlet Web 应用提供无状态 JWT 认证、接口放行与授权规则，以及登录令牌签发（`TokenIssuer`）。基于 Java 17、Spring Boot 3.5。

## 引入依赖

```xml
<dependency>
    <groupId>com.trionesdev.springboot</groupId>
    <artifactId>security-spring-boot-starter</artifactId>
</dependency>
```

版本由 `com.trionesdev.springboot:spring-boot-dependencies` 统一管理。默认安全链需要容器中有 `ActorContext` Bean（引入 Triones `spring-boot-starter` 即可）。

## 认证模式（`triones.security.authorization.mode`）

| 模式 | 安全链 | 令牌签发 | 所需模块 |
| --- | --- | --- | --- |
| `jwt`（默认） | 本 starter 的 `securityWebFilterChain`，用 `JwtFacade` 校验 | `JwtTokenIssuer`（`JwtFacade` 签名） | 本 starter |
| `oauth2` | `security-oauth2-spring-boot-starter` 的资源服务器链 | 嵌入式授权服务器的 `EmbeddedOauth2TokenIssuer` | 另加 `security-oauth2-spring-boot-starter`、`oauth2-authorization-server-spring-boot-starter` |

两种模式共用下文的 `triones.security.*` 放行与授权规则。以下内容默认指 `jwt` 模式。

## 快速配置

```yaml
triones:
  security:
    ignored-matchers:
      - /api/auth/login
      - /api/public/**
    jwt:
      algorithm: RS256
      expiration: 1800
      key:
        private-key-location: file:/etc/app/jwt-private.pem
```

登录接口由应用实现：校验用户凭据后用 `TokenIssuer` 签发令牌（见下文）。其余请求需要携带 `Authorization: Bearer <token>`。

## 默认行为

- 启用 Web Security，创建 `JwtFacade` 和名为 `securityWebFilterChain` 的 `SecurityFilterChain`，安装 `JwtAuthenticationFilter`。
- 会话策略为 `STATELESS`；默认关闭 CSRF 与匿名认证（可配置）。
- 先放行 `default-ignored-matchers` + `ignored-matchers`，再应用 `rules`，其余请求要求 `authenticated()`；未认证返回 `JwtAuthenticationEntryPoint` 的 401 JSON。
- 默认仅放行 `/favicon.ico`。Swagger、Actuator、WebSocket 等路径需要显式配置。

## 配置项

### 放行与授权规则（前缀 `triones.security`）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `csrf` | `false` | 是否启用 CSRF 防护 |
| `anonymous` | `false` | 是否启用匿名认证；`rules` 中的 `ANONYMOUS` 规则需要它为 `true` |
| `default-ignored-matchers` | `["/favicon.ico"]` | 默认放行列表，配置后整体替换；设为 `[]` 可移除默认项 |
| `ignored-matchers` | `[]` | 追加放行的路径（所有 HTTP 方法） |
| `rules` | `[]` | 按顺序生效的授权规则，见下表 |

放行路径调用 `requestMatchers(...).permitAll()`，请求仍经过安全过滤器链。路径示例：`/api/public/**`。

`rules[n]` 字段：

| 字段 | 说明 |
| --- | --- |
| `matchers` | 路径列表 |
| `method` | HTTP 方法，可选；省略时匹配所有方法 |
| `action` | `AUTHENTICATED`（默认）、`PERMIT_ALL`、`DENY_ALL`、`ANONYMOUS`、`HAS_ROLE`、`HAS_ANY_ROLE`、`HAS_AUTHORITY`、`HAS_ANY_AUTHORITY` |
| `roles` | `HAS_ROLE`（只能一个）/ `HAS_ANY_ROLE` 使用，不带 `ROLE_` 前缀 |
| `authorities` | `HAS_AUTHORITY`（只能一个）/ `HAS_ANY_AUTHORITY` 使用 |

```yaml
triones:
  security:
    ignored-matchers:
      - /api/auth/login
    rules:
      - matchers: [/api/public/**]
        method: GET
        action: PERMIT_ALL
      - matchers: [/api/admin/**]
        action: HAS_ANY_ROLE
        roles: [ADMIN, OPERATOR]
      - matchers: [/internal/**]
        action: DENY_ALL
```

- 规则按书写顺序匹配，先匹配的生效；放行路径排在所有规则之前，规则无法收紧已放行的路径。
- `HAS_ROLE` / `HAS_AUTHORITY` 配置多个值时启动失败，多个值请用 `HAS_ANY_*`；`roles` / `authorities` 为空时该规则被忽略。


### JWT（前缀 `triones.security.jwt`）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `algorithm` | `RS256` | 签名算法：`HS256/384/512`、`RS256/384/512`、`PS256/384/512`、`ES256/384/512` |
| `secret` | 未设置 | HMAC 算法的密钥 |
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
- `JwtFacade.getPublicJwkSet()` 返回签名公钥（第一个）和所有验证公钥，可用于需要公开公钥的场景。
- HMAC 轮换：`secret` 换成新值，旧密钥以 oct 类型 JWK 文件放入 `verification-keys[n].jwk-location`。

算法族由 `algorithm` 自动推导，不需要配置 `family`。`algorithm` 使用 `Algorithm` 枚举，支持 YAML 绑定及 IDE 配置候选值提示：`HS256`、`HS384`、`HS512`、`RS256`、`RS384`、`RS512`、`PS256`、`PS384`、`PS512`、`ES256`、`ES384`、`ES512`。

```yaml
triones:
  security:
    jwt:
      algorithm: HS256
      secret: ${JWT_SECRET}
      expiration: 1800
```

## 签发令牌 `TokenIssuer`

`jwt` 模式下自动注册 `JwtTokenIssuer`（应用自定义 `TokenIssuer` 时退让）。登录接口校验用户后调用：

```java
@RequiredArgsConstructor
@RestController
public class AuthController {
    private final TokenIssuer tokenIssuer;

    @PostMapping("/api/auth/login")
    public AuthToken login(@RequestBody LoginCmd cmd) {
        User user = userService.authenticate(cmd.getUsername(), cmd.getPassword());
        JwtClaims claims = new JwtClaims();          // role、userId、tenantId 等，写入令牌供 JwtActorResolver 解析
        claims.setUserId(user.getId());
        claims.setRole(user.getRole());
        return tokenIssuer.issue(user.getId(), claims);
    }

    @PostMapping("/api/auth/refresh")
    public AuthToken refresh(@RequestParam String refreshToken) {
        return tokenIssuer.refresh(refreshToken);
    }
}
```

`JwtTokenIssuer` 的 `accessToken` 与 `refreshToken` 是同一个 JWT。`refresh` 先校验令牌（签名、未过期），再签发新令牌：沿用 `sub` 与业务声明（role、userId、tenantId 等），`iat` 为当前时间，`exp` 为当前时间 + `expiration`，即**从刷新时刻起延长有效期**；`nbf`、`jti` 不沿用。

- 令牌过期后不能刷新，只能重新登录；客户端应在过期前刷新。
- 任何未过期的令牌都能刷新，旧令牌在自身过期前仍然有效，无法单独撤销。需要独立、可撤销、轮换的 refresh token 时使用 `oauth2` 模式。

`oauth2` 模式下不注册 `JwtTokenIssuer`：`JwtFacade` 的签名密钥与 OAuth2 资源服务器校验的密钥不同，令牌由授权服务器 starter 的 `EmbeddedOauth2TokenIssuer` 签发，接口不变。


## 扩展与集成

应用可按需提供以下 Bean，自动配置会通过 `ObjectProvider` 获取并传入 JWT 认证过滤器：

| 类型 | 用途 |
| --- | --- |
| `com.trionesdev.spring.security.jwt.JwtActorResolver` | 从 JWT claims 解析 `Actor` |
| `com.trionesdev.spring.security.ActorAuthoritiesResolver` | 根据 `Actor` 解析权限（供 `rules` 中的角色 / 权限规则使用） |
| `com.trionesdev.commons.core.jwt.JwtFacade` | 替换默认 JWT 组件，配置校验由应用负责 |
| `com.trionesdev.spring.security.TokenIssuer` | 替换默认令牌签发 |

`JwtFacadeConfiguration` 独立创建 JWT 组件，不依赖 `ActorContext` 与 Web 环境，可用于非 Web 应用。

安全链仅在 Servlet Web 环境、`authorization.mode` 为 `jwt` 时配置；应用提供任意 `SecurityFilterChain` Bean 时，默认安全链退让。

默认 JWT 配置在启动时检查算法和有效期。HMAC 必须显式提供 secret：HS256 上限为 32、HS384 为 48、HS512 为 64 个 UTF-8 字节。不足时由底层 `JwtFacade` 在字节数组尾部补零（`0x00`，不是字符 `0`），超出时启动报错。RSA/EC 的密钥兼容性由 `JwtFacade` 构造时检查。

## 排查问题

- **缺少 `ActorContext` Bean**：引入 Triones `spring-boot-starter`，或自行注册 `ActorContext`。
- **放行的接口仍返回 401**：检查路径是否匹配；`rules` 中的 `PERMIT_ALL` 带了 `method` 时只对该方法生效。
- **`ANONYMOUS` 规则不生效**：需要同时配置 `triones.security.anonymous: true`。
- **重启或多实例部署后令牌无法验证**：启动日志出现 `No persistent JWT signing key configured` 说明 RSA/EC 使用了临时密钥，请配置 `triones.security.jwt.key.*`；HMAC 则需保持算法和 secret 一致。
- **`oauth2` 模式下没有 `TokenIssuer` Bean**：需要引入 `oauth2-authorization-server-spring-boot-starter` 并配置平台客户端。
