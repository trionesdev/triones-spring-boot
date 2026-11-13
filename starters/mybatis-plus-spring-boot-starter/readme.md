# MybatisPlus Spring Boot Starter

整合 MyBatis-Plus 与 Triones 的 MyBatis-Plus 公共组件，统一依赖版本。**本模块只做依赖聚合，不包含自动配置**，下文组件需要在应用中按需注册。

## 引入依赖

```xml
<dependency>
    <groupId>com.trionesdev.springboot</groupId>
    <artifactId>mybatis-plus-spring-boot-starter</artifactId>
</dependency>
```

版本由 `com.trionesdev.springboot:spring-boot-dependencies` 统一管理。引入内容：

| 依赖 | 说明 |
| --- | --- |
| `com.baomidou:mybatis-plus-spring-boot3-starter` | MyBatis-Plus 官方 Spring Boot 3 starter（其自带的 `mybatis-spring` 被排除，改用统一管理的版本） |
| `org.mybatis:mybatis-spring` | 与 Spring 6 匹配的版本 |
| `com.trionesdev.commons:commons-mybatis-plus` | Triones 公共组件，见下文 |

MyBatis-Plus 本身的配置（`mybatis-plus.*`、数据源等）与官方用法一致。

## 公共组件（`com.trionesdev.commons.mybatisplus`）

### 基础持久化对象

| 类 | 字段 |
| --- | --- |
| `po.BasePO` | `createdAt`（插入时填充）、`updatedAt`（插入 / 更新时填充） |
| `po.BaseLogicPO` | 继承 `BasePO`，增加逻辑删除 `is_deleted`（`false` / `true`）、`createdBy`、`updatedBy` |
| `entity.BaseEntity` | `createdAt`、`createdBy`、`updatedAt`、`updatedBy` |

```java
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_order")
public class OrderPO extends BaseLogicPO {
    @TableId
    private String id;
    private String tenantId;
}
```

字段上声明的是 `@TableField(fill = ...)`，**实际填充需要应用注册 MyBatis-Plus 的 `MetaObjectHandler`**，例如从 `ActorContext` 取当前用户写入 `createdBy`。

### 主键生成

`incrementer.TrionesIdentifierGenerator` 实现 `IdentifierGenerator`（雪花算法），可指定 workerId / dataCenterId，适合多实例部署时显式分配：

```java
@Bean
public IdentifierGenerator identifierGenerator() {
    return new TrionesIdentifierGenerator(workerId, dataCenterId);
}
```

### 多租户插件

`plugins.inner.TenantLineMultiInnerInterceptor` 与 `plugins.handler.TenantLineMultiHandler` 扩展 MyBatis-Plus 的租户行插件，需要加入应用的 `MybatisPlusInterceptor`。

### 类型处理器（`typehandlers`）

把集合 / 枚举字段与数据库列互转：`StringListTypeHandler`、`LongListTypeHandler`、`SetTypeHandler`、`ListTypeHandler`、`EnumTypeHandler` 等，以及 Integer、Long、Double、Float、Boolean、String 的 `*CollectionTypeHandler`。

```java
@TableName(value = "t_order", autoResultMap = true)
public class OrderPO {
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> tags;
}
```

### 分页工具

- `util.MpPageUtils.page(criteria)`：把分页查询条件转为 MyBatis-Plus 的 `IPage`。
- `util.MpPageUtils.of(page)` / `of(page, data)`：把 `IPage` 转为接口返回的分页对象，`data` 用于替换为转换后的 DTO 列表。
