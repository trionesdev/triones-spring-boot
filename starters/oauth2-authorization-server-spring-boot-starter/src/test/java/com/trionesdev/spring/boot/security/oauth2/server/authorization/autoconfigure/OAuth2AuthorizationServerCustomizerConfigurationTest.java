package com.trionesdev.spring.boot.security.oauth2.server.authorization.autoconfigure;

import com.trionesdev.commons.model.AuthToken;
import com.trionesdev.spring.security.TokenIssuer;
import com.trionesdev.spring.security.oauth2.server.authorization.EmbeddedOauth2TokenIssuer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class OAuth2AuthorizationServerCustomizerConfigurationTest {
    private static final String CLIENT = "triones.security.oauth2.authorization-server.default-client.";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            // 宿主为 MVC 应用：securityMatcher 需要 mvcHandlerMappingIntrospector
            .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class,
                    Oauth2AuthorizationServerConfiguration.class, TokenIssuerAutoConfiguration.class))
            .withUserConfiguration(JdbcConfig.class);

    @BeforeEach
    void bindRequest() {
        // 授权服务器端点之外签发时，issuer 取当前请求地址
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void resetRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void jwtModeDoesNotStartAuthorizationServer() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBeansOfType(RegisteredClientRepository.class).isEmpty());
            assertTrue(context.getBeansOfType(SecurityFilterChain.class).isEmpty());
            assertTrue(context.getBeansOfType(TokenIssuer.class).isEmpty());
        });
    }

    @Test
    void oauth2ModeRegistersDefaultClientAndIssuesRefreshableTokens() {
        runner.withPropertyValues("triones.security.authorization.mode=oauth2",
                        CLIENT + "client-id=platform",
                        CLIENT + "client-secret=secret",
                        CLIENT + "scopes=openid")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    RegisteredClient client = context.getBean(RegisteredClientRepository.class).findByClientId("platform");
                    assertNotNull(client);
                    assertTrue(client.getClientSecret().startsWith("{bcrypt}"));
                    assertTrue(PasswordEncoderFactories.createDelegatingPasswordEncoder()
                            .matches("secret", client.getClientSecret()));
                    assertTrue(client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN));

                    TokenIssuer issuer = context.getBean(TokenIssuer.class);
                    assertInstanceOf(EmbeddedOauth2TokenIssuer.class, issuer);
                    AuthToken token = issuer.issue("user-1", null);
                    assertNotNull(token.getAccessToken());
                    assertNotNull(token.getRefreshToken());
                    assertNotEquals(token.getAccessToken(), token.getRefreshToken());

                    AuthToken refreshed = issuer.refresh(token.getRefreshToken());
                    assertNotNull(refreshed.getAccessToken());
                    // 默认轮换 refresh_token
                    assertNotEquals(token.getRefreshToken(), refreshed.getRefreshToken());
                });
    }

    @Test
    void existingClientIsNotOverwritten() {
        runner.withPropertyValues("triones.security.authorization.mode=oauth2",
                        CLIENT + "client-id=platform",
                        CLIENT + "client-secret=new-secret")
                .withUserConfiguration(PreRegisteredClient.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    RegisteredClient client = context.getBean(RegisteredClientRepository.class).findByClientId("platform");
                    assertEquals("{noop}old-secret", client.getClientSecret());
                });
    }

    @Test
    void appliesAllCustomizersInOrder() {
        runner.withPropertyValues("triones.security.authorization.mode=oauth2",
                        CLIENT + "client-id=platform",
                        CLIENT + "client-secret=secret")
                .withUserConfiguration(TwoCustomizers.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(List.of("first", "second"), context.getBean(TwoCustomizers.class).invoked);
                });
    }

    @Test
    void embeddedIssuerRequiresClientId() {
        runner.withPropertyValues("triones.security.authorization.mode=oauth2")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Configuration(proxyBeanMethods = false)
    static class JdbcConfig {
        @Bean(destroyMethod = "shutdown")
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .setName(UUID.randomUUID().toString())
                    .addScript("org/springframework/security/oauth2/server/authorization/oauth2-authorization-schema.sql")
                    .addScript("org/springframework/security/oauth2/server/authorization/oauth2-authorization-consent-schema.sql")
                    .addScript("org/springframework/security/oauth2/server/authorization/client/oauth2-registered-client-schema.sql")
                    .build();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }

    /**
     * 两个实现：声明顺序与 @Order 相反，验证按 @Order 执行
     */
    @Configuration(proxyBeanMethods = false)
    static class TwoCustomizers {
        final List<String> invoked = new CopyOnWriteArrayList<>();

        @Bean
        @Order(2)
        OAuth2AuthorizationServerCustomizer secondCustomizer() {
            return configurer -> invoked.add("second");
        }

        @Bean
        @Order(1)
        OAuth2AuthorizationServerCustomizer firstCustomizer() {
            return configurer -> invoked.add("first");
        }
    }

    /**
     * 模拟库中已有同 client-id 的客户端：在注册器执行前写入
     */
    @Configuration(proxyBeanMethods = false)
    static class PreRegisteredClient {
        @Bean
        Object preRegisteredPlatformClient(RegisteredClientRepository repository) {
            repository.save(RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("platform")
                    .clientSecret("{noop}old-secret")
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .build());
            return new Object();
        }
    }
}
