package com.trionesdev.spring.boot.auth.client.autoconfigure;

import com.trionesdev.spring.security.auth.client.AccessToken;
import com.trionesdev.spring.security.auth.client.AuthorizedClient;
import com.trionesdev.spring.security.auth.client.AuthorizedClientManager;
import com.trionesdev.spring.security.auth.client.ClientCredentialsTokenResponseClient;
import com.trionesdev.spring.security.auth.client.ClientRegistration;
import com.trionesdev.spring.security.auth.client.ClientRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AuthClientAutoConfigurationTest {
    private static final String PREFIX = "triones.security.auth.client.";

    // 非 Web 环境：服务间调用在定时任务等场景同样可用
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuthClientAutoConfiguration.class));

    @Test
    void noRegistrationNoBeans() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertFalse(context.containsBean("authorizedClientManager"));
            assertTrue(context.getBeansOfType(ClientRegistrationRepository.class).isEmpty());
        });
    }

    @Test
    void mapsRegistrationWithSharedProvider() {
        runner.withPropertyValues(
                        PREFIX + "registration.order-service.provider=triones-auth",
                        PREFIX + "registration.order-service.client-id=order",
                        PREFIX + "registration.order-service.client-secret=secret",
                        PREFIX + "registration.order-service.authorization-grant-type=client_credentials",
                        PREFIX + "registration.order-service.client-authentication-method=client_secret_post",
                        PREFIX + "registration.order-service.scope=order.read,order.write",
                        PREFIX + "provider.triones-auth.token-uri=http://auth/oauth2/token")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(AuthorizedClientManager.class));
                    ClientRegistration registration = context.getBean(ClientRegistrationRepository.class)
                            .findByRegistrationId("order-service");
                    assertEquals("order", registration.getClientId());
                    assertEquals("secret", registration.getClientSecret());
                    assertEquals("http://auth/oauth2/token", registration.getTokenUri());
                    assertEquals(Set.of("order.read", "order.write"), registration.getScopes());
                    assertEquals(ClientRegistration.ClientAuthenticationMethod.CLIENT_SECRET_POST,
                            registration.getClientAuthenticationMethod());
                });
    }

    @Test
    void providerDefaultsToRegistrationIdAndBasicAuth() {
        runner.withPropertyValues(
                        PREFIX + "registration.auth.client-id=svc",
                        PREFIX + "provider.auth.token-uri=http://auth/token")
                .run(context -> {
                    ClientRegistration registration = context.getBean(ClientRegistrationRepository.class)
                            .findByRegistrationId("auth");
                    assertEquals("http://auth/token", registration.getTokenUri());
                    assertEquals(ClientRegistration.ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                            registration.getClientAuthenticationMethod());
                });
    }

    @Test
    void managerUsesConfiguredRegistration() {
        AtomicInteger calls = new AtomicInteger();
        ClientCredentialsTokenResponseClient stub = registration -> {
            calls.incrementAndGet();
            return AuthorizedClient.builder()
                    .clientRegistration(registration)
                    .accessToken(AccessToken.builder()
                            .tokenValue("token-for-" + registration.getClientId())
                            .build())
                    .tokenType("Bearer")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
        };
        runner.withBean(ClientCredentialsTokenResponseClient.class, () -> stub)
                .withPropertyValues(
                        PREFIX + "registration.order-service.client-id=order",
                        PREFIX + "provider.order-service.token-uri=http://auth/token")
                .run(context -> {
                    AuthorizedClientManager manager = context.getBean(AuthorizedClientManager.class);
                    assertEquals("token-for-order", manager.getAccessToken("order-service"));
                    assertEquals("token-for-order", manager.getAccessToken("order-service"));
                    assertEquals(1, calls.get());
                });
    }

    @Test
    void clockSkewIsApplied() {
        AtomicInteger calls = new AtomicInteger();
        // token 还有 10 分钟过期，clock-skew 为 15 分钟时每次都应重新申请
        ClientCredentialsTokenResponseClient stub = registration -> {
            calls.incrementAndGet();
            return AuthorizedClient.builder()
                    .clientRegistration(registration)
                    .accessToken(AccessToken.builder().tokenValue("t").build())
                    .tokenType("Bearer")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(600))
                    .build();
        };
        runner.withBean(ClientCredentialsTokenResponseClient.class, () -> stub)
                .withPropertyValues(
                        PREFIX + "clock-skew=15m",
                        PREFIX + "registration.a.client-id=svc",
                        PREFIX + "provider.a.token-uri=http://auth/token")
                .run(context -> {
                    AuthorizedClientManager manager = context.getBean(AuthorizedClientManager.class);
                    manager.getAccessToken("a");
                    manager.getAccessToken("a");
                    assertEquals(2, calls.get());
                });
    }

    @Test
    void unsupportedGrantTypeFailsStartup() {
        runner.withPropertyValues(
                        PREFIX + "registration.a.client-id=svc",
                        PREFIX + "registration.a.authorization-grant-type=authorization_code",
                        PREFIX + "provider.a.token-uri=http://auth/token")
                .run(context -> assertStartupFailure(context.getStartupFailure(), "authorization-grant-type"));
    }

    @Test
    void missingTokenUriFailsStartup() {
        runner.withPropertyValues(
                        PREFIX + "registration.a.client-id=svc",
                        PREFIX + "registration.a.provider=missing")
                .run(context -> assertStartupFailure(context.getStartupFailure(), "provider.missing.token-uri"));
    }

    @Test
    void unsupportedAuthenticationMethodFailsStartup() {
        runner.withPropertyValues(
                        PREFIX + "registration.a.client-id=svc",
                        PREFIX + "registration.a.client-authentication-method=private_key_jwt",
                        PREFIX + "provider.a.token-uri=http://auth/token")
                .run(context -> assertStartupFailure(context.getStartupFailure(), "client-authentication-method"));
    }

    @Test
    void userBeansTakePrecedence() {
        AuthorizedClientManager custom = new AuthorizedClientManager(id -> null);
        runner.withBean(AuthorizedClientManager.class, () -> custom)
                .withPropertyValues(
                        PREFIX + "registration.a.client-id=svc",
                        PREFIX + "provider.a.token-uri=http://auth/token")
                .run(context -> assertSame(custom, context.getBean(AuthorizedClientManager.class)));
    }

    private static void assertStartupFailure(Throwable failure, String expectedMessagePart) {
        assertNotNull(failure);
        Throwable cause = failure;
        while (cause != null && (cause.getMessage() == null || !cause.getMessage().contains(expectedMessagePart))) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "expected failure mentioning " + expectedMessagePart + " but got " + failure);
    }
}
