package com.trionesdev.spring.boot.security.oauth2.server.authorization.autoconfigure;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.trionesdev.spring.security.jwt.JwtAuthenticationEntryPoint;
import com.trionesdev.spring.security.oauth2.server.authorization.ActorTokenCustomizer;
import com.trionesdev.spring.security.oauth2.server.authorization.EmbeddedOauth2TokenIssuer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.LinkedHashMap;
import java.util.Set;

import static com.trionesdev.spring.security.oauth2.server.authorization.RASLoader.loadRsaKey;


/**
 * 嵌入式 OAuth2 授权服务器，仅在 {@code triones.security.authorization.mode=oauth2} 时生效：
 * jwt 模式下资源服务器用 JwtFacade 校验，这里签发的令牌无法通过，且不应额外要求 JDBC 与授权端点。
 */
@RequiredArgsConstructor
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(HttpSecurity.class)
@ConditionalOnProperty(value = "triones.security.authorization.mode", havingValue = "oauth2")
@EnableWebSecurity
@EnableConfigurationProperties(value = {
        AuthorizationServerProperties.class
})
public class Oauth2AuthorizationServerConfiguration {
    private static final String DEFAULT_LOGIN_PAGE = "/login";

    private final ObjectProvider<OAuth2AuthorizationServerCustomizer> authorizationServerCustomizers;

    @Bean
    public JWKSource<SecurityContext> jwkSource(AuthorizationServerProperties properties) throws Exception {
        return new ImmutableJWKSet<>(new JWKSet(loadRsaKey(properties.getJwk().getKeyStore())));
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }


    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        var builder = AuthorizationServerSettings.builder();
        return builder.build();
    }

    /**
     * 启动时把 {@code default-client} 写入 oauth2_registered_client（已存在则不覆盖）
     */
    @Bean
    @ConditionalOnProperty(prefix = "triones.security.oauth2.authorization-server.default-client", name = "client-id")
    public DefaultClientRegistrar defaultClientRegistrar(AuthorizationServerProperties properties,
                                                         RegisteredClientRepository registeredClientRepository,
                                                         ObjectProvider<PasswordEncoder> passwordEncoder) {
        return new DefaultClientRegistrar(properties.getDefaultClient(), registeredClientRepository,
                passwordEncoder.getIfUnique(PasswordEncoderFactories::createDelegatingPasswordEncoder));
    }

    /**
     * 令牌生成器：授权服务器端点与 {@link EmbeddedOauth2TokenIssuer} 共用
     */
    @Bean
    public OAuth2TokenGenerator<OAuth2Token> tokenGenerator(JWKSource<SecurityContext> jwkSource) {
        var jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
        jwtGenerator.setJwtCustomizer(new ActorTokenCustomizer());
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, new OAuth2AccessTokenGenerator(), new OAuth2RefreshTokenGenerator());
    }

    /**
     * 授权服务器端点
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                                                                      AuthorizationServerProperties properties,
                                                                      OAuth2TokenGenerator<OAuth2Token> tokenGenerator) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();
        var authenticationEntryPoint = unauthenticatedEntryPoint(properties.getLogin());
        http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, authorizationServer -> {
                    authorizationServer
                            .tokenGenerator(tokenGenerator)
                            .oidc(Customizer.withDefaults());
                    // 在默认配置之后按 @Order 依次应用，后执行的可覆盖先执行的
                    authorizationServerCustomizers.orderedStream()
                            .forEach(customizer -> customizer.customize(authorizationServer));
                })
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                // 未登录：按 login.unauthenticated 跳转登录页或返回 401 JSON；/oauth2/token 等非浏览器请求始终 401 JSON
                .exceptionHandling(exceptionHandling -> exceptionHandling.authenticationEntryPoint(authenticationEntryPoint))
                // /userinfo 使用 access_token 认证
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * 授权登录页：默认 /login 使用 Spring Security 内置登录页；配置其他路径时页面由应用提供，表单 POST 到同一路径。
     * 登录成功后回到被拦截的授权请求（会话中保存的原请求）。
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public SecurityFilterChain authorizationServerLoginSecurityFilterChain(HttpSecurity http,
                                                                           AuthorizationServerProperties properties) throws Exception {
        String loginPage = loginPage(properties.getLogin());
        http
                .securityMatcher(loginPage)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .formLogin(form -> {
                    // 显式设置 loginPage 会关闭内置登录页，默认路径保持内置页
                    if (!DEFAULT_LOGIN_PAGE.equals(loginPage)) {
                        form.loginPage(loginPage);
                    }
                });
        return http.build();
    }

    /**
     * redirect：浏览器请求（Accept 含 text/html，不含 *&#47;*）跳转登录页，其他请求返回 401 JSON；
     * json：全部返回 401 JSON
     */
    static AuthenticationEntryPoint unauthenticatedEntryPoint(AuthorizationServerProperties.Login login) {
        var json = new JwtAuthenticationEntryPoint();
        String loginPage = loginPage(login);
        if (login.getUnauthenticated() == AuthorizationServerProperties.Unauthenticated.JSON) {
            return json;
        }
        var browser = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        // curl、HTTP 客户端默认 Accept: */*，不应被当成浏览器跳转
        browser.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
        entryPoints.put(browser, new LoginUrlAuthenticationEntryPoint(loginPage));
        var delegating = new DelegatingAuthenticationEntryPoint(entryPoints);
        delegating.setDefaultEntryPoint(json);
        return delegating;
    }

    private static String loginPage(AuthorizationServerProperties.Login login) {
        String page = login.getPage();
        if (page == null || !page.startsWith("/")) {
            throw new IllegalArgumentException("triones.security.oauth2.authorization-server.login.page must start "
                    + "with '/': " + page);
        }
        return page;
    }


}
