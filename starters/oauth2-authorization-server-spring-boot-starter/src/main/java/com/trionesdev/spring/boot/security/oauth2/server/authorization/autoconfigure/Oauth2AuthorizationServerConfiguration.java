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
import org.springframework.security.web.SecurityFilterChain;

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
    private static final String LOGIN_PAGE = "/login";

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
     * 客户端（oauth2_registered_client）
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcOperations jdbcOperations) {
        return new JdbcRegisteredClientRepository(jdbcOperations);
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
     * 授权记录（oauth2_authorization）
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcOperations jdbcOperations,
                                                           RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository);
    }

    /**
     * 用户授权同意（oauth2_authorization_consent）
     */
    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(JdbcOperations jdbcOperations,
                                                                         RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcOperations, registeredClientRepository);
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
                                                                      OAuth2TokenGenerator<OAuth2Token> tokenGenerator) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();
        var authenticationEntryPoint = new JwtAuthenticationEntryPoint();
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
                // 浏览器访问授权端点且未登录时跳转登录页；/oauth2/token 等接口认证失败仍返回 401
                .exceptionHandling(exceptionHandling -> exceptionHandling.authenticationEntryPoint(authenticationEntryPoint))
                // /userinfo 使用 access_token 认证
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * 授权登录页（Spring Security 内置表单登录页）
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public SecurityFilterChain authorizationServerLoginSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(LOGIN_PAGE)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .formLogin(Customizer.withDefaults());
        return http.build();
    }


}
