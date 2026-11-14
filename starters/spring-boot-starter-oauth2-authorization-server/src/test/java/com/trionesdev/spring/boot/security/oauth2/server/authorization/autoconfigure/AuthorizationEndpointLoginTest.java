package com.trionesdev.spring.boot.security.oauth2.server.authorization.autoconfigure;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 未登录访问授权端点时的处理：login.unauthenticated / login.page
 */
class AuthorizationEndpointLoginTest {
    private static final String PREFIX = "triones.security.oauth2.authorization-server.";
    private static final String AUTHORIZE = "/oauth2/authorize?response_type=code&client_id=web&scope=openid"
            + "&redirect_uri=https://app.example.com/callback";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class,
                    Oauth2AuthorizationServerConfiguration.class, TokenIssuerAutoConfiguration.class))
            .withUserConfiguration(OAuth2AuthorizationServerCustomizerConfigurationTest.JdbcConfig.class)
            .withPropertyValues("triones.security.authorization.mode=oauth2",
                    PREFIX + "default-client.client-id=web",
                    PREFIX + "default-client.client-secret=secret",
                    PREFIX + "default-client.authorization-grant-types=authorization_code,refresh_token",
                    PREFIX + "default-client.redirect-uris=https://app.example.com/callback",
                    PREFIX + "default-client.scopes=openid");

    private final WebApplicationContextRunner redirectRunner =
            runner.withPropertyValues(PREFIX + "login.unauthenticated=redirect");

    private static MockMvc mvc(ApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    private static MockHttpServletResponse authorize(ApplicationContext context, MediaType accept) throws Exception {
        return mvc(context).perform(get(AUTHORIZE).accept(accept)).andReturn().getResponse();
    }

    private static void assertJson401(MockHttpServletResponse response) {
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE), response.getContentType());
    }

    @Test
    void jsonByDefaultEvenForBrowsers() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertJson401(authorize(context, MediaType.TEXT_HTML));
        });
    }

    @Test
    void redirectModeSendsBrowserToDefaultLoginPage() {
        redirectRunner.run(context -> {
            assertNull(context.getStartupFailure());
            MockHttpServletResponse response = authorize(context, MediaType.TEXT_HTML);
            assertEquals(302, response.getStatus());
            assertTrue(response.getRedirectedUrl().endsWith("/login"), response.getRedirectedUrl());
        });
    }

    @Test
    void redirectModeStillReturnsJsonForNonBrowserRequests() {
        redirectRunner.run(context -> {
            assertJson401(authorize(context, MediaType.APPLICATION_JSON));
            // curl 等默认 Accept: */*
            assertJson401(authorize(context, MediaType.ALL));
        });
    }

    @Test
    void customLoginPage() {
        redirectRunner.withPropertyValues(PREFIX + "login.page=/sign-in")
                .run(context -> {
                    MockHttpServletResponse response = authorize(context, MediaType.TEXT_HTML);
                    assertEquals(302, response.getStatus());
                    assertTrue(response.getRedirectedUrl().endsWith("/sign-in"), response.getRedirectedUrl());
                    // 自定义路径时不再生成内置登录页，/login 不属于登录链
                    assertNotEquals(200, mvc(context).perform(get("/login")).andReturn().getResponse().getStatus());
                });
    }

    @Test
    void defaultLoginPageIsGenerated() {
        runner.run(context -> {
            MockHttpServletResponse response = mvc(context).perform(get("/login")).andReturn().getResponse();
            assertEquals(200, response.getStatus());
            assertTrue(response.getContentAsString().contains("<form"));
        });
    }

    @Test
    void afterLoginBrowserReturnsToAuthorizationRequest() {
        redirectRunner.withBean(UserDetailsService.class, () -> new InMemoryUserDetailsManager(
                        User.withUsername("alice").password("{noop}pwd").roles("USER").build()))
                .run(context -> {
                    MockMvc mvc = mvc(context);
                    MockHttpSession session = new MockHttpSession();
                    // 1. 未登录访问授权端点 -> 跳转登录页，原请求保存在会话中
                    MvcResult denied = mvc.perform(get(AUTHORIZE).accept(MediaType.TEXT_HTML).session(session)).andReturn();
                    assertEquals(302, denied.getResponse().getStatus());

                    // 2. 内置登录页带 CSRF token
                    String page = mvc.perform(get("/login").session(session)).andReturn().getResponse().getContentAsString();
                    Matcher csrf = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page);
                    assertTrue(csrf.find(), page);

                    // 3. 登录成功 -> 回到原授权请求
                    MvcResult login = mvc.perform(post("/login").session(session)
                                    .param("username", "alice").param("password", "pwd")
                                    .param("_csrf", csrf.group(1)))
                            .andReturn();
                    assertEquals(302, login.getResponse().getStatus());
                    String target = login.getResponse().getRedirectedUrl();
                    assertTrue(target.contains("/oauth2/authorize") && target.contains("client_id=web"), target);
                });
    }

    @Test
    void loginPageMustStartWithSlash() {
        runner.withPropertyValues(PREFIX + "login.page=sign-in")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }
}
