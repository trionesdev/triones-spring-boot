# OpenFeign Spring Boot Starter

为 Spring Cloud OpenFeign 提供 Triones 统一的错误解码与日志开关。

## 引入依赖

```xml
<dependency>
    <groupId>com.trionesdev.springboot</groupId>
    <artifactId>openfeign-spring-boot-starter</artifactId>
</dependency>
<!-- starter 中为 provided，应用需要自行引入 -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

版本由 `com.trionesdev.springboot:spring-boot-dependencies` 统一管理（含 Spring Cloud BOM）。

## 自动配置内容（`FeignAutoConfiguration`）

| Bean | 说明 |
| --- | --- |
| `ErrorDecoder` | `DefaultErrorDecoder`，把下游的错误响应统一转为 `InternalRequestException` |
| `Logger.Level` | `triones.feign.logger.enable=true` 时为 `FULL` |

### 错误解码规则

| 下游状态码 | 结果 |
| --- | --- |
| 500 | `InternalRequestException`，message：`Internal Request Internal Exception` |
| 401 | `InternalRequestException`，message：`Internal Request Not Authentication` |
| 404 | `InternalRequestException`，message：`Internal Request Not Found` |
| 其他 | 尝试按 `ErrorResponse`（`code`、`message`）解析响应体并记录日志，抛出 `InternalRequestException`，message：`Internal Request Unknown Exception` |

异常 code 统一为 `INTERNAL_REQUEST_EXCEPTION`，请求 URL 与方法写入 error 日志，便于排查。下游的原始错误码只记录在日志中，不会透传给调用方。

## 配置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `triones.feign.logger.enable` | `false` | 为 `true` 时 Feign 日志级别为 `FULL`（请求 / 响应头和体） |

Feign 日志通过 SLF4J 以 DEBUG 级别输出，还需要打开对应 Feign 客户端接口的日志级别：

```yaml
triones:
  feign:
    logger:
      enable: true
logging:
  level:
    com.example.client: debug     # Feign 客户端接口所在的包
```

`FULL` 会记录请求体与响应体（可能包含令牌、个人信息），生产环境谨慎开启。

## 注意

- 两个 Bean 都没有 `@ConditionalOnMissingBean`。应用如需自定义全局 `ErrorDecoder`，建议放到具体 `@FeignClient(configuration = ...)` 的配置类中，避免容器中出现两个同类型 Bean。
- 本 starter 不处理认证：服务间调用需要携带令牌时，由应用注册 Feign `RequestInterceptor`。
