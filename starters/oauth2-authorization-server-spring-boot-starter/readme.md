# OAuth2 Authorization Server Spring Boot Starter

把 Spring Authorization Server 嵌入业务应用：提供标准 OAuth2 / OIDC 端点，客户端、授权记录持久化到数据库，并为平台登录提供 `EmbeddedOauth2TokenIssuer`（签发可刷新、可撤销的 access_token + refresh_token）。

## 引入依赖

```xml
<dependency>
    <groupId>com.trionesdev.springboot</groupId>
    <artifactId>oauth2-authorization-server-spring-boot-starter</artifactId>
</dependency>
<!-- 业务接口的令牌校验 -->
<dependency>
    <groupId>com.trionesdev.springboot</groupId>
    <artifactId>security-oauth2-spring-boot-starter</artifactId>
</dependency>
```

版本由 `com.trionesdev.springboot:spring-boot-dependencies` 统一管理。宿主需要是 Servlet Web（Spring MVC）应用，并提供 `JdbcOperations`（例如配置数据源后由 Spring Boot 创建的 `JdbcTemplate`）。

## 快速开始

```yaml
triones:
  security:
    authorization:
      mode: oauth2                       # 必须：仅 oauth2 模式下启用授权服务器
    oauth2:
      authorization-server:
        jwk:
          key-store:                     # 令牌签名密钥（RSA）
            location: file:/etc/app/oauth2-jwk.p12
            password: ${OAUTH2_KEYSTORE_PASSWORD}
            alias: oauth2
        default-client:                  # 平台登录客户端，启动时写入数据库
          client-id: platform
          client-secret: ${OAUTH2_PLATFORM_SECRET}
          scopes: [openid]
```

数据库表需要提前创建，建表脚本在 `spring-security-oauth2-authorization-server` jar 中：

| 表 | 脚本 |
| --- | --- |
| `oauth2_registered_client` | `org/springframework/security/oauth2/server/authorization/client/oauth2-registered-client-schema.sql` |
| `oauth2_authorization` | `org/springframework/security/oauth2/server/authorization/oauth2-authorization-schema.sql` |
| `oauth2_authorization_consent` | `org/springframework/security/oauth2/server/authorization/oauth2-authorization-consent-schema.sql` |

脚本使用通用 SQL 类型（`blob`、`timestamp` 等），PostgreSQL 等数据库需要调整为对应类型（如 `bytea` / `text`），建议放入 Flyway / Liquibase 迁移管理。

## 平台登录：`TokenIssuer`

`oauth2` 模式下注册 `EmbeddedOauth2TokenIssuer` 作为 `TokenIssuer`，登录接口的写法与 `jwt` 模式相同（见 security starter readme）：

```java
AuthToken token = tokenIssuer.issue(user.getId(), claims);   // access_token + refresh_token
AuthToken refreshed = tokenIssuer.refresh(refreshToken);      // 换新的 access_token
```

- 使用 `default-client.client-id` 对应的客户端签发；未配置 client-id 时启动失败，客户端不存在时登录抛出 `Platform oauth2 client not registered`。
- `JwtClaims` 中的平台声明（role、userId、tenantId 等）写入 access_token，资源服务器解析出的 `Actor` 与 `jwt` 模式一致。
- 授权记录保存在 `oauth2_authorization`；refresh_token 默认每次刷新轮换（旧的立即失效），`reuse-refresh-tokens: true` 时沿用。
- refresh_token 无效、过期、已撤销或不是平台登录签发时抛出 `BusinessException`（code `INVALID_REFRESH_TOKEN`）。
- `triones.security.oauth2.authorization-server.mode` 默认 `embedded`；设为其他值时不注册 `EmbeddedOauth2TokenIssuer`。

## 配置项（前缀 `triones.security.oauth2.authorization-server`）

### 签名密钥 `jwk.key-store`

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `location` | 未设置 | keystore 文件，支持 `file:`、`classpath:` |
| `type` | `PKCS12` | `PKCS12` 或 `JKS` |
| `password` | 未设置 | keystore 密码 |
| `alias` | 未设置 | RSA 密钥别名 |
| `key-password` | 同 `password` | 私钥密码 |

未配置 `location` 时启动时随机生成 RSA 密钥并输出 WARN：重启后已签发的令牌全部失效，多实例之间也互不认可，只能用于开发环境。

```bash
keytool -genkeypair -keyalg RSA -keysize 2048 -alias oauth2 -dname "CN=oauth2" -validity 3650 \
  -storetype PKCS12 -keystore oauth2-jwk.p12 -storepass changeit
```

### 默认客户端 `default-client`

配置了 `client-id` 时，启动时（Web 服务器开始接收请求前）写入 `oauth2_registered_client`。**已存在同 client-id 的客户端时不覆盖**，以数据库为准；修改配置后需要手动更新或删除库中的记录。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `client-id` | 未设置 | 客户端 ID；平台登录必填 |
| `client-name` | 同 `client-id` | 显示名称 |
| `client-secret` | 未设置 | 明文时用容器的 `PasswordEncoder` 编码入库（未注册时为 `{bcrypt}`）；带 `{bcrypt}` 等前缀时按原值入库。为空表示公共客户端 |
| `client-authentication-methods` | 有密钥：`client_secret_basic` + `client_secret_post`；无密钥：`none` | 客户端认证方式 |
| `authorization-grant-types` | `client_credentials`, `refresh_token` | 授权类型；平台登录需要包含 `refresh_token` |
| `scopes` | `[]` | 允许的 scope |
| `redirect-uris` | `[]` | `authorization_code` 回调地址 |
| `post-logout-redirect-uris` | `[]` | OIDC 登出后的跳转地址 |
| `require-proof-key` | `false` | 是否强制 PKCE；公共客户端始终强制 |
| `require-authorization-consent` | `false` | 是否显示授权同意页 |
| `access-token-ttl` | `2h` | access_token 有效期 |
| `refresh-token-ttl` | `30d` | refresh_token 有效期 |
| `reuse-refresh-tokens` | `false` | 刷新时是否沿用 refresh_token |

公共客户端（无 `client-secret`）不能使用 `client_credentials`，否则启动失败。

其他第三方客户端直接写入 `oauth2_registered_client`，或通过 `RegisteredClientRepository.save` 注册。

### 未登录处理 `login`

浏览器访问授权端点（`/oauth2/authorize` 等）而用户未登录时的处理方式：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `login.unauthenticated` | `json` | `json`：返回 401 JSON；`redirect`：跳转登录页，登录成功后回到原授权请求 |
| `login.page` | `/login` | 登录页路径，必须以 `/` 开头（相对 context path），否则启动失败 |

```yaml
triones:
  security:
    oauth2:
      authorization-server:
        login:
          unauthenticated: redirect
          page: /sign-in
```

- 默认 `json`，浏览器走授权码流程（`authorization_code`）时需要配置为 `redirect`。
- `redirect` 模式下只有浏览器请求会跳转：`Accept` 含 `text/html` 且不是 `*/*`。`/oauth2/token`、curl、前端 `fetch`（JSON）等请求始终返回 401 JSON，两种模式相同。
- `page` 为默认 `/login` 时使用 Spring Security 内置登录页；配置其他路径时内置页关闭，页面由应用提供（Controller 或静态页），登录表单以 `username`、`password`、`_csrf` POST 到同一路径。页面引用的 CSS / JS 等静态资源需要加入 `triones.security.ignored-matchers`。
- 登录需要应用提供 `UserDetailsService`（或 `AuthenticationProvider`）。
- 原授权请求保存在 HTTP 会话中，多实例部署时需要会话粘滞或共享会话（如 Spring Session）。
- `page` 只支持本应用内的路径，不支持跳转到其他域名的登录页。

## 端点

启用 OIDC，使用 Spring Authorization Server 的默认路径：

| 端点 | 路径 |
| --- | --- |
| 授权 | `/oauth2/authorize` |
| 令牌 | `/oauth2/token` |
| JWKS | `/oauth2/jwks` |
| 令牌内省 / 撤销 | `/oauth2/introspect`、`/oauth2/revoke` |
| 服务发现 | `/.well-known/openid-configuration`、`/.well-known/oauth-authorization-server` |
| 用户信息 | `/userinfo`（使用 access_token 认证） |
| 登录页 | `login.page`，默认 `/login`（Spring Security 内置表单登录） |

issuer 未单独配置，取请求地址（与 Spring Authorization Server 默认行为一致）。外部服务通过 `issuer-uri` 校验令牌时，访问授权服务器使用的地址必须与令牌中的 `iss` 一致。

`client_credentials` 示例：

```bash
curl -u order-service:secret -d grant_type=client_credentials -d scope=order.read \
  http://localhost:8080/oauth2/token
```

## 自定义端点：`OAuth2AuthorizationServerCustomizer`

注册一个实现了 `OAuth2AuthorizationServerCustomizer` 的 Bean，在 starter 默认配置（令牌生成器、OIDC）之后调整 `OAuth2AuthorizationServerConfigurer`，例如自定义 userinfo、增加授权类型：

```java
@Component
@RequiredArgsConstructor
public class PlatformAuthorizationServerCustomizer implements OAuth2AuthorizationServerCustomizer {
    private final UserService userService;

    @Override
    public void customize(OAuth2AuthorizationServerConfigurer configurer) {
        configurer.oidc(oidc -> oidc.userInfoEndpoint(userInfo -> userInfo
                .userInfoMapper(context -> new OidcUserInfo(
                        userService.loadUserInfoClaims(context.getAuthorization().getPrincipalName())))));
    }
}
```

- `customize` 只在启动构建安全链时调用一次；每个请求的业务逻辑放在注册的组件中（`userInfoMapper`、`AuthenticationProvider` 等）。
- 支持多个实现，按 `@Order`（或 `Ordered`）依次调用，都在 starter 默认配置之后执行；后执行的可覆盖先执行的同一项配置。可以按职责拆分，例如一个负责 userinfo、一个负责自定义授权类型。

## 自动配置的 Bean

| Bean | 说明 |
| --- | --- |
| `JWKSource` / `JwtDecoder` | 签名密钥与对应的令牌解码器（资源服务器链也使用此 `JwtDecoder`） |
| `RegisteredClientRepository` | `JdbcRegisteredClientRepository` |
| `OAuth2AuthorizationService` | `JdbcOAuth2AuthorizationService` |
| `OAuth2AuthorizationConsentService` | `JdbcOAuth2AuthorizationConsentService` |
| `OAuth2TokenGenerator` | JWT access_token（`ActorTokenCustomizer` 写入 `client_id` 与平台 `ACTOR_*` 声明）+ refresh_token |
| `authorizationServerSecurityFilterChain` | 授权服务器端点，`@Order(HIGHEST_PRECEDENCE)` |
| `authorizationServerLoginSecurityFilterChain` | `login.page` 表单登录 |
| `DefaultClientRegistrar` | 配置了 `default-client.client-id` 时注册默认客户端 |
| `TokenIssuer` | `EmbeddedOauth2TokenIssuer` |

## 注意

- 平台登录走 `TokenIssuer`，不经过 `/oauth2/token`；第三方客户端走标准端点。
