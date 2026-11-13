# TrionesDev Spring Boot 基础公共包

基于 Spring Boot 3.5、Java 17。Spring Boot 2.x 版本见 https://github.com/trionesdev/triones-spring-boot-starters/tree/boot2

版本统一由 BOM 管理：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.trionesdev.springboot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${triones-spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

| 模块 | 说明 |
| --- | --- |
| [spring-boot-starter](starters/spring-boot-starter) | 基础能力：`ActorContext`、审计 / 事件 / 权限切面、缓存门面、分布式锁、API 版本控制 |
| [security-spring-boot-starter](starters/security-spring-boot-starter) | 无状态 JWT 认证（默认 `jwt` 模式）、放行与授权规则、`TokenIssuer` |
| [security-oauth2-spring-boot-starter](starters/security-oauth2-spring-boot-starter) | `oauth2` 模式的资源服务器：校验 OAuth2 access token |
| [oauth2-authorization-server-spring-boot-starter](starters/oauth2-authorization-server-spring-boot-starter) | 嵌入式 OAuth2 / OIDC 授权服务器与平台登录令牌签发 |
| [mybatis-plus-spring-boot-starter](starters/mybatis-plus-spring-boot-starter) | MyBatis-Plus 依赖整合与公共组件 |
| [openfeign-spring-boot-starter](starters/openfeign-spring-boot-starter) | OpenFeign 统一错误解码与日志开关 |
| [opentelemetry-spring-boot-starter](starters/opentelemetry-spring-boot-starter) | 基于 OpenTelemetry Baggage 跨服务透传 `Actor` |

#### 互相吹捧，共同进步

> 扫码关注公众号，获取最新技术文章，关注我，可以直接沟通。
<div style="width: 100%;text-align: center">
<img src="images/shuque_wx.jpg" width="200px" alt="">
</div>