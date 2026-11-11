package com.trionesdev.spring.boot.authorization.server.autoconfigure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.trionesdev.commons.context.actor.ActorContext;
import com.trionesdev.commons.core.jwt.JwtFacade;
import com.trionesdev.spring.boot.security.autoconfigure.SecurityConfiguration;
import com.trionesdev.spring.boot.security.autoconfigure.jwt.JwtFacadeConfiguration;
import com.trionesdev.spring.boot.security.autoconfigure.jwt.JwtSecurityConfiguration;
import com.trionesdev.spring.security.authorization.server.token.InMemoryRegisteredClientRepository;
import com.trionesdev.spring.security.authorization.server.token.RegisteredClient;
import com.trionesdev.spring.security.authorization.server.token.RegisteredClientRepository;
import com.trionesdev.spring.security.authorization.server.token.TokenClaimsCustomizer;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通过真实的 springSecurityFilterChain（授权服务器链 + security starter 默认链）验证端点。
 */
class AuthorizationServerAutoConfigurationTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityConfiguration.class, JwtFacadeConfiguration.class,
                    JwtSecurityConfiguration.class, AuthorizationServerAutoConfiguration.class))
            .withBean(ActorContext.class, ActorContext::new)
            .withPropertyValues("triones.security.jwt.algorithm=ES256",
                    "triones.security.authorization-server.issuer=https://auth.example.com",
                    "triones.security.authorization-server.token.clients[0].client-id=order-service",
                    "triones.security.authorization-server.token.clients[0].client-secret={noop}secret",
                    "triones.security.authorization-server.token.clients[0].scopes=order.read,order.write");

    private static MockHttpServletResponse execute(ApplicationContext context, MockHttpServletRequest request)
            throws Exception {
        Filter chain = context.getBean("springSecurityFilterChain", Filter.class);
        MockHttpServletResponse response = new MockHttpServletResponse();
        chain.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletResponse get(ApplicationContext context, String uri) throws Exception {
        return execute(context, new MockHttpServletRequest("GET", uri));
    }

    private static MockHttpServletResponse token(ApplicationContext context, String uri, String clientId,
                                                 String secret, String scope) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContentType("application/x-www-form-urlencoded");
        request.addHeader("Authorization", "Basic " + Base64.getEncoder()
                .encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8)));
        request.addParameter("grant_type", "client_credentials");
        if (scope != null) {
            request.addParameter("scope", scope);
        }
        return execute(context, request);
    }

    @Test
    void issuesTokenFromConfiguredClient() {
        runner.withPropertyValues("triones.security.authorization-server.token.access-token-time-to-live=10m")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    MockHttpServletResponse response = token(context, "/auth/token", "order-service", "secret",
                            "order.read");
                    assertEquals(200, response.getStatus(), response.getContentAsString());
                    JsonNode body = MAPPER.readTree(response.getContentAsString());
                    assertEquals("Bearer", body.get("token_type").asText());
                    assertEquals(600, body.get("expires_in").asInt());
                    assertEquals("order.read", body.get("scope").asText());

                    JWTClaimsSet claims = context.getBean(JwtFacade.class).parse(body.get("access_token").asText());
                    assertEquals("order-service", claims.getSubject());
                    assertEquals("https://auth.example.com", claims.getIssuer());
                });
    }

    @Test
    void rejectsWrongSecret() {
        runner.run(context -> assertEquals(401,
                token(context, "/auth/token", "order-service", "wrong", null).getStatus()));
    }

    @Test
    void servesJwksAndKeepsDefaultChainForOtherRequests() {
        runner.run(context -> {
            MockHttpServletResponse response = get(context, "/auth/jwks");
            assertEquals(200, response.getStatus());
            JWKSet set = JWKSet.parse(response.getContentAsString());
            assertEquals(context.getBean(JwtFacade.class).getSigningKeyId(), set.getKeys().get(0).getKeyID());
            // security starter 的默认资源服务器链仍然生效
            assertTrue(context.containsBean("securityWebFilterChain"));
            assertEquals(401, get(context, "/api/users").getStatus());
        });
    }

    @Test
    void customPaths() {
        runner.withPropertyValues("triones.security.authorization-server.token.path=/oauth2/token",
                        "triones.security.authorization-server.jwks.path=/oauth2/jwks")
                .run(context -> {
                    assertEquals(200, token(context, "/oauth2/token", "order-service", "secret", null).getStatus());
                    assertEquals(200, get(context, "/oauth2/jwks").getStatus());
                    assertEquals(401, get(context, "/auth/jwks").getStatus());
                });
    }

    @Test
    void tokenEndpointDisabled() {
        runner.withPropertyValues("triones.security.authorization-server.token.enabled=false")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertFalse(context.containsBean("registeredClientRepository"));
                    assertEquals(401, token(context, "/auth/token", "order-service", "secret", null).getStatus());
                    assertEquals(200, get(context, "/auth/jwks").getStatus());
                });
    }

    @Test
    void allEndpointsDisabled() {
        runner.withPropertyValues("triones.security.authorization-server.token.enabled=false",
                        "triones.security.authorization-server.jwks.enabled=false")
                .run(context -> {
                    assertFalse(context.containsBean(AuthorizationServerAutoConfiguration.SECURITY_FILTER_CHAIN_BEAN_NAME));
                    assertEquals(401, get(context, "/auth/jwks").getStatus());
                });
    }

    @Test
    void disabled() {
        runner.withPropertyValues("triones.security.authorization-server.enabled=false")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertFalse(context.containsBean(AuthorizationServerAutoConfiguration.SECURITY_FILTER_CHAIN_BEAN_NAME));
                    assertTrue(context.getBeansOfType(RegisteredClientRepository.class).isEmpty());
                });
    }

    @Test
    void customRepositoryAndClaimsCustomizers() {
        runner.withUserConfiguration(CustomBeans.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(401, token(context, "/auth/token", "order-service", "secret", null).getStatus());
                    MockHttpServletResponse response = token(context, "/auth/token", "billing", "pwd", null);
                    assertEquals(200, response.getStatus(), response.getContentAsString());
                    JWTClaimsSet claims = context.getBean(JwtFacade.class)
                            .parse(MAPPER.readTree(response.getContentAsString()).get("access_token").asText());
                    // 按 @Order 依次执行，后者覆盖前者
                    assertEquals("second", claims.getClaim("tenant"));
                    assertEquals("SERVICE", claims.getClaim("role"));
                });
    }

    @Test
    void hmacRequiresJwksDisabled() {
        runner.withPropertyValues("triones.security.jwt.algorithm=HS256", "triones.security.jwt.secret=secret")
                .run(context -> assertNotNull(context.getStartupFailure()));
        runner.withPropertyValues("triones.security.jwt.algorithm=HS256", "triones.security.jwt.secret=secret",
                        "triones.security.authorization-server.jwks.enabled=false")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(200, token(context, "/auth/token", "order-service", "secret", null).getStatus());
                });
    }

    @Test
    void invalidClientFailsStartup() {
        runner.withPropertyValues("triones.security.authorization-server.token.clients[1].client-id=no-secret")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomBeans {
        @Bean
        RegisteredClientRepository customRepository() {
            return new InMemoryRegisteredClientRepository(
                    RegisteredClient.withClientId("billing").clientSecret("{noop}pwd").build());
        }

        @Bean
        @Order(1)
        TokenClaimsCustomizer first() {
            return (claims, client, scopes) -> claims.claim("tenant", "first").claim("role", "SERVICE");
        }

        @Bean
        @Order(2)
        TokenClaimsCustomizer second() {
            return (claims, client, scopes) -> claims.claim("tenant", "second");
        }
    }
}
