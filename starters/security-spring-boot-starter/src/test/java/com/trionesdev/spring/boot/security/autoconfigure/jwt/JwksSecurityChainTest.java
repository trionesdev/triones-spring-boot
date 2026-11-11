package com.trionesdev.spring.boot.security.autoconfigure.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.trionesdev.commons.context.actor.ActorContext;
import com.trionesdev.commons.core.jwt.JwtFacade;
import com.trionesdev.spring.boot.security.autoconfigure.SecurityConfiguration;
import com.trionesdev.spring.security.jwks.JwksEndpointConfigurer;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过真实的 Spring Security 过滤器链（springSecurityFilterChain）验证 JWKS 端点。
 */
class JwksSecurityChainTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityConfiguration.class, JwtFacadeConfiguration.class,
                    JwtSecurityConfiguration.class))
            .withBean(ActorContext.class, ActorContext::new)
            .withPropertyValues("triones.security.jwt.algorithm=ES256");

    private static MockHttpServletResponse get(ApplicationContext context, String uri) throws Exception {
        Filter chain = context.getBean("springSecurityFilterChain", Filter.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        chain.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void servedByDefaultChainWithoutPermitAll() {
        runner.withPropertyValues("triones.security.jwt.jwks.enabled=true",
                        "triones.security.jwt.jwks.cache-max-age=60s")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    MockHttpServletResponse response = get(context, "/auth/jwks");
                    assertEquals(200, response.getStatus());
                    JWKSet set = JWKSet.parse(response.getContentAsString());
                    assertEquals(context.getBean(JwtFacade.class).getSigningKeyId(), set.getKeys().get(0).getKeyID());
                    // 经过安全链：HeaderWriterFilter 的安全响应头生效，端点自身的 Cache-Control 不被覆盖
                    assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
                    assertEquals("public, max-age=60", response.getHeader("Cache-Control"));

                    // 其他接口仍需认证
                    assertEquals(401, get(context, "/api/users").getStatus());
                });
    }

    @Test
    void customPath() {
        runner.withPropertyValues("triones.security.jwt.jwks.enabled=true",
                        "triones.security.jwt.jwks.path=/.well-known/jwks.json")
                .run(context -> {
                    assertEquals(200, get(context, "/.well-known/jwks.json").getStatus());
                    assertEquals(401, get(context, "/auth/jwks").getStatus());
                });
    }

    @Test
    void disabledByDefault() {
        runner.run(context -> assertEquals(401, get(context, "/auth/jwks").getStatus()));
    }

    @Test
    void hmacWithJwksEnabledFailsStartup() {
        runner.withPropertyValues("triones.security.jwt.algorithm=HS256",
                        "triones.security.jwt.secret=secret",
                        "triones.security.jwt.jwks.enabled=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void customSecurityFilterChainWithDefaults() {
        runner.withUserConfiguration(CustomChainWithDefaults.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    MockHttpServletResponse response = get(context, JwksEndpointConfigurer.DEFAULT_PATH);
                    assertEquals(200, response.getStatus());
                    assertEquals("public, max-age=300", response.getHeader("Cache-Control"));
                    assertEquals(403, get(context, "/api/users").getStatus());
                });
    }

    @Test
    void customSecurityFilterChainWithStarterProperties() {
        runner.withUserConfiguration(CustomChainWithProperties.class)
                .withPropertyValues("triones.security.jwt.jwks.path=/keys",
                        "triones.security.jwt.jwks.cache-max-age=0")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    MockHttpServletResponse response = get(context, "/keys");
                    assertEquals(200, response.getStatus());
                    assertEquals("no-store", response.getHeader("Cache-Control"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomChainWithDefaults {
        @Bean
        SecurityFilterChain customChain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
                    .with(new JwksEndpointConfigurer<>(), Customizer.withDefaults());
            return http.build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomChainWithProperties {
        @Bean
        SecurityFilterChain customChain(HttpSecurity http, JwtSecurityProperties properties) throws Exception {
            JwtSecurityProperties.Jwks jwks = properties.getJwks();
            http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
                    .with(new JwksEndpointConfigurer<HttpSecurity>().path(jwks.getPath()).cacheMaxAge(jwks.getCacheMaxAge()),
                            Customizer.withDefaults());
            return http.build();
        }
    }
}
