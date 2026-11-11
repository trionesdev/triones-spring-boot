# Triones:SpringBoot:AuthClient

`com.trionesdev.commons:spring-auth-client` 的 Spring Boot 自动配置：根据 `triones.security.auth.client.*` 创建服务间调用（client_credentials）所需的 Bean。适用于定时任务、MQ 消费、系统内部调用等**没有用户 token** 的场景。

## 引入依赖

```xml
<dependency>
    <groupId>com.trionesdev.springboot</groupId>
    <artifactId>auth-client-spring-boot-starter</artifactId>
</dependency>
```

版本由 `spring-boot-dependencies` 统一管理。本 starter 不依赖 Web 环境，也不依赖 `security-spring-boot-starter`，两者可独立或同时使用。

## 配置（前缀 `triones.security.auth.client`）

结构与 Spring Security 的 `spring.security.oauth2.client.*` 一致：

```yaml
triones:
  security:
    auth:
      client:
        registration:
          order-service:                 # registrationId，获取 token 时使用
            provider: triones-auth       # 省略时与 registrationId 同名
            client-id: order-service
            client-secret: ${ORDER_CLIENT_SECRET}
            authorization-grant-type: client_credentials   # 默认，目前仅支持该模式
            client-authentication-method: client_secret_basic  # 或 client_secret_post
            scope: order.read,order.write
        clock-skew: 60s                  # token 到期前多久重新申请，默认 60 秒
        provider:
          triones-auth:
            token-uri: http://localhost:9000/oauth2/token
```

配置了至少一个 `registration` 时，`AuthClientAutoConfiguration` 创建以下 Bean（均可由应用覆盖）：

| Bean | 默认实现 |
| --- | --- |
| `ClientRegistrationRepository` | `InMemoryClientRegistrationRepository`，由上述配置转换 |
| `ClientCredentialsTokenResponseClient` | `OkHttpClientCredentialsTokenResponseClient` |
| `AuthorizedClientManager` | 缓存 token，到期前 `clock-skew`（默认 60 秒）自动重新申请 |

grant type 或认证方式不支持、缺少 `client-id`、找不到 provider 的 `token-uri` 时启动失败。

拦截器按 registrationId 绑定目标服务，需要自行声明：

```java
// Feign：有用户 token 时透传，没有时使用服务 token
@Bean
public RequestInterceptor orderServiceAuthInterceptor(AuthorizedClientManager manager) {
    return new AuthorizedClientFeignRequestInterceptor(manager, "order-service");
}

// RestTemplate / RestClient
restTemplate.getInterceptors().add(new AuthorizedClientHttpRequestInterceptor(manager, "order-service"));

// OkHttp
new OkHttpClient.Builder().addInterceptor(new AuthorizedClientOkHttpInterceptor(manager, "order-service")).build();

// 直接获取
String token = manager.getAccessToken("order-service");
```

Feign 的 `RequestInterceptor` 声明为全局 Bean 时会作用于所有 Feign 客户端，只想用于某个服务时，放到该 `@FeignClient(configuration = ...)` 的配置类中（该配置类不要被组件扫描到）。

组件本身的行为（缓存、并发、401 处理、JWKS 校验等）见 `spring-auth-client` 的 readme。
