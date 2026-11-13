# Spring Boot Starter

Triones 基础 starter：注册请求上下文、JSON 工具、审计 / 事件 / 权限切面、缓存门面、分布式锁与 API 版本控制等通用能力。其他 Triones starter（security、opentelemetry 等）依赖其中的 `ActorContext`。

## 引入依赖

```xml
<dependency>
    <groupId>com.trionesdev.springboot</groupId>
    <artifactId>spring-boot-starter</artifactId>
</dependency>
```

版本由 `com.trionesdev.springboot:spring-boot-dependencies` 统一管理。`spring-boot-starter-web`、`redisson`、`caffeine`、`spring-data-redis` 为 optional 依赖，用到对应能力时由应用自行引入。

## 功能一览

| 功能 | 自动配置类 | 生效条件 | 配置前缀 |
| --- | --- | --- | --- |
| 请求上下文 `ActorContext` | `ContextAutoConfiguration` | 始终 | — |
| JSON 工具、审计、事件、权限切面 | `CoreAutoConfiguration` | 始终 | `triones.app` |
| 异常码文案 | `ExceptionAutoConfiguration` | 始终 | `triones.exception` |
| 分布式锁 `@Lock` | `LockAutoConfiguration` | 始终 | `triones.lock` |
| 缓存门面 `CacheFacade` | `CacheFacadeAutoConfiguration` | 始终，实现按 `spring.cache.type` 选择 | `spring.cache` |
| API 版本控制 | `ApiVersionAutoConfiguration` | Web 应用且配置了 `type` | `triones.web.api-version` |

## 请求上下文

注册 `com.trionesdev.commons.context.actor.ActorContext`，保存当前请求的操作者（`Actor`：actorId、userId、role、tenantId 等）。security starter 的认证过滤器在认证成功后写入，业务代码直接注入读取：

```java
@RequiredArgsConstructor
@Service
public class OrderService {
    private final ActorContext actorContext;

    public void create(OrderCmd cmd) {
        String tenantId = actorContext.getActor().getTenantId();
        // ...
    }
}
```

## 应用配置（前缀 `triones.app`）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `multi-tenant` | `false` | 是否多租户 |
| `self-hosted` | `true` | 是否自部署（不对外开放） |

## JSON 工具

容器中的 `ObjectMapper` Bean 创建后会注入 `com.trionesdev.commons.core.util.JsonUtils`，使静态工具与 Spring MVC 使用同一套 Jackson 配置（日期格式、模块等）。

## 操作审计 `@OperationAudit`

在方法或类上标注 `@OperationAudit`，由 `OperationAuditAspect` 收集操作信息并交给 `OperationAuditHandler` 处理（如落库、发 MQ）：

```java
@OperationAudit(domain = "order", action = "cancel", subjectId = "#orderId", description = "取消订单")
public void cancel(String orderId) { ... }
```

```java
@Component
public class DbAuditHandler extends OperationAuditHandler {
    @Override
    public Boolean isDefault() {
        return true;          // 未在注解中指定 handler 时使用
    }

    @Override
    public void handle(OperationAuditContext context) {
        // 保存审计记录
    }
}
```

- 注解的 `handler` 指定处理器类型；未指定时使用 `isDefault()` 为 `true` 的处理器，存在多个默认处理器会报错。
- 可重写 `beforeContent` / `afterContent` 记录变更前后的内容。
- 容器中没有任何 `OperationAuditHandler` 时不做审计。

## 事件切面 `@ActEvent*`

在方法执行的不同时机执行一段 SpEL，常用于发布领域事件。表达式可通过 `@beanName` 引用 Bean、通过 `#参数名` 引用方法参数：

| 注解 | 时机 |
| --- | --- |
| `@ActEventBefore` | 方法执行前 |
| `@ActEventAfter` | 方法执行后（无论成功失败） |
| `@ActEventAfterReturning` | 正常返回后 |
| `@ActEventAfterThrowing` | 抛出异常后 |
| `@ActEventAround(before = "...", after = "...")` | 前后各执行一次 |

```java
@ActEventAfterReturning("@orderEventPublisher.created(#cmd)")
public void create(OrderCmd cmd) { ... }
```

## 方法权限 `@ActPermission`

执行方法前计算 SpEL，结果不为 `true` 时抛出 `PermissionDeniedException`：

```java
@ActPermission(value = "@permissionChecker.canEdit(#orderId)", description = "编辑订单")
public void update(String orderId, OrderCmd cmd) { ... }
```

## 异常码文案（前缀 `triones.exception`）

`resource-paths` 指定 `ResourceBundle` 资源，`TrionesException` 等按异常 code 查找对应语言的文案，`{0}` 等占位符按 `MessageFormat` 填充：

```yaml
triones:
  exception:
    resource-paths:
      - classpath:i18n/exceptions      # 对应 i18n/exceptions.properties、i18n/exceptions_zh_CN.properties
```

```properties
# i18n/exceptions_zh_CN.properties
ORDER_NOT_FOUND=订单 {0} 不存在
```

## 分布式锁 `@Lock`（前缀 `triones.lock`）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `type` | `thread` | `thread`（JVM 内锁）或 `redis`（Redisson）；未设置时为 `thread`，即使类路径上有 Redisson |

```java
@Lock(key = "'order:' + #orderId", waitTime = 3000, leaseTime = 10000)
public void pay(String orderId) { ... }
```

- `key` 为 SpEL；`waitTime`、`leaseTime` 的单位由 `unit` 指定，默认毫秒。
- 获取锁失败抛出 `RuntimeException("lock failed")`。
- `redis` 需要引入 `redisson` 并提供 `RedissonClient` Bean，否则没有可用的 `TrionesLockTemplate`，启动失败。多实例部署时必须显式配置 `type: redis`，`thread` 只在单个 JVM 内互斥。
- 也可以注入 `TrionesLockTemplate` 手动加锁；应用自定义该 Bean 时默认实现退让。

## 缓存门面 `CacheFacade`

在 Spring Cache 之上提供带过期时间、无需预先声明 cache name 的读写接口：

```java
@RequiredArgsConstructor
@Service
public class CaptchaService {
    private final CacheFacade<String, String> cacheFacade;

    public void save(String phone, String code) {
        cacheFacade.set("captcha:" + phone, code, Duration.ofMinutes(5));
    }

    public String get(String phone) {
        return cacheFacade.get("captcha:" + phone);
    }
}
```

- 实现按 `spring.cache.type` 选择（`redis`、`caffeine`、`simple` 等，与 Spring Boot 的 `CacheType` 一致）。**未配置时使用 `SimpleCacheFacade`**（基于 `CacheManager` 的通用实现），不会因为类路径上有 Redis 而自动切换；要用 Redis 必须显式配置：

  ```yaml
  spring:
    cache:
      type: redis
  ```
- Redis 实现使用容器中的 `RedisTemplate` 与 `CacheManager`；Caffeine 实现读取 `spring.cache.caffeine.spec`。
- 应用自定义 `CacheFacade` Bean 时默认实现退让。

## API 版本控制（前缀 `triones.web.api-version`）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `type` | 未设置（不启用） | `path` 或 `header` |
| `path.version-name` | `version` | `path` 模式下 URI 中版本变量名 |
| `header.version-name` | `X-Version` | `header` 模式下的请求头名 |

**路径版本**

```yaml
triones:
  web:
    api-version:
      type: path
```

```java
@RestController
@RequestMapping("/api/{version}/orders")
public class OrderController {
    @PathApiVersion("v1")
    @GetMapping("/{id}")
    public OrderV1 getV1(@PathVariable String id) { ... }

    @PathApiVersion("v2")
    @GetMapping("/{id}")
    public OrderV2 getV2(@PathVariable String id) { ... }
}
```

**请求头版本**：`type: header`，方法上标注 `@HeaderApiVersion("v2")`，请求携带 `X-Version: v2`。

版本控制通过替换 Spring MVC 的 `RequestMappingHandlerMapping` 实现（`WebMvcRegistrations`），同一应用中只能有一个 `WebMvcRegistrations` 提供该映射。
