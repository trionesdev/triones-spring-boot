package com.trionesdev.spring.boot.authorization.server.autoconfigure;

import com.trionesdev.commons.core.jwt.JwtFacade;
import com.trionesdev.spring.boot.security.autoconfigure.SecurityConfiguration;
import com.trionesdev.spring.boot.security.autoconfigure.jwt.JwtFacadeConfiguration;
import com.trionesdev.spring.boot.security.autoconfigure.jwt.JwtSecurityConfiguration;
import com.trionesdev.spring.security.authorization.server.jwks.JwksEndpointConfigurer;
import com.trionesdev.spring.security.authorization.server.token.ClientAuthenticationMethod;
import com.trionesdev.spring.security.authorization.server.token.InMemoryRegisteredClientRepository;
import com.trionesdev.spring.security.authorization.server.token.RegisteredClient;
import com.trionesdev.spring.security.authorization.server.token.RegisteredClientRepository;
import com.trionesdev.spring.security.authorization.server.token.TokenClaimsCustomizer;
import com.trionesdev.spring.security.authorization.server.token.TokenEndpointConfigurer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConfigurationCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 授权服务器自动配置：在独立的、最高优先级的 {@link SecurityFilterChain}（只匹配 token / JWKS 端点路径）中
 * 挂载 {@link TokenEndpointConfigurer} 与 {@link JwksEndpointConfigurer}，不影响 security starter 的默认资源服务器安全链。
 * <ul>
 *     <li>签名使用容器中的 {@link JwtFacade}（{@code triones.security.jwt.*}）；</li>
 *     <li>{@link RegisteredClientRepository} 未自定义时由 {@code token.clients} 构建内存仓库；</li>
 *     <li>{@link TokenClaimsCustomizer} Bean 按 {@code @Order} 依次执行；</li>
 *     <li>定义名为 {@value #SECURITY_FILTER_CHAIN_BEAN_NAME} 的 Bean 可整体替换该安全链。</li>
 * </ul>
 */
@AutoConfiguration(after = {SecurityAutoConfiguration.class, SecurityConfiguration.class,
        JwtFacadeConfiguration.class, JwtSecurityConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({HttpSecurity.class, TokenEndpointConfigurer.class})
@ConditionalOnProperty(prefix = AuthorizationServerProperties.PREFIX, name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(AuthorizationServerProperties.class)
public class AuthorizationServerAutoConfiguration {
    public static final String SECURITY_FILTER_CHAIN_BEAN_NAME = "authorizationServerSecurityFilterChain";

    private static final Log log = LogFactory.getLog(AuthorizationServerAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = AuthorizationServerProperties.PREFIX + ".token", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public RegisteredClientRepository registeredClientRepository(AuthorizationServerProperties properties) {
        List<RegisteredClient> clients = new ArrayList<>();
        for (AuthorizationServerProperties.Client client : properties.getToken().getClients()) {
            clients.add(toRegisteredClient(client));
        }
        if (clients.isEmpty()) {
            log.warn("No client registered for the token endpoint: configure "
                    + AuthorizationServerProperties.PREFIX + ".token.clients or define a RegisteredClientRepository bean");
        }
        return new InMemoryRegisteredClientRepository(clients);
    }

    /**
     * 只匹配端点路径，端点过滤器位于认证 / 授权过滤器之前直接写出响应；其余请求交给后续安全链。
     */
    @Bean(name = SECURITY_FILTER_CHAIN_BEAN_NAME)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnMissingBean(name = SECURITY_FILTER_CHAIN_BEAN_NAME)
    @Conditional(AnyEndpointEnabled.class)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
                                                                      AuthorizationServerProperties properties,
                                                                      JwtFacade jwtFacade,
                                                                      ObjectProvider<RegisteredClientRepository> registeredClientRepository,
                                                                      ObjectProvider<TokenClaimsCustomizer> claimsCustomizers)
            throws Exception {
        AuthorizationServerProperties.Token token = properties.getToken();
        AuthorizationServerProperties.Jwks jwks = properties.getJwks();
        List<String> paths = new ArrayList<>();

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a.anyRequest().denyAll());

        if (token.isEnabled()) {
            RegisteredClientRepository repository = registeredClientRepository.getIfUnique();
            if (repository == null) {
                throw new IllegalStateException("A single RegisteredClientRepository bean is required for the token endpoint");
            }
            http.with(new TokenEndpointConfigurer<HttpSecurity>()
                            .path(token.getPath())
                            .issuer(properties.getIssuer())
                            .accessTokenTimeToLive(token.getAccessTokenTimeToLive())
                            .claimsCustomizer(compose(claimsCustomizers.orderedStream().toList()))
                            .registeredClientRepository(repository)
                            .jwtFacade(jwtFacade),
                    Customizer.withDefaults());
            paths.add(token.getPath());
        }
        if (jwks.isEnabled()) {
            http.with(new JwksEndpointConfigurer<>(jwtFacade, jwks.getPath(), jwks.getCacheMaxAge()),
                    Customizer.withDefaults());
            paths.add(jwks.getPath());
        }
        http.securityMatcher(pathMatcher(Set.copyOf(paths)));
        return http.build();
    }

    static RegisteredClient toRegisteredClient(AuthorizationServerProperties.Client client) {
        RegisteredClient.Builder builder = RegisteredClient.withClientId(client.getClientId())
                .clientSecret(client.getClientSecret())
                .scopes(client.getScopes())
                .accessTokenTimeToLive(client.getAccessTokenTimeToLive());
        Set<ClientAuthenticationMethod> methods = client.getClientAuthenticationMethods();
        if (methods != null && !methods.isEmpty()) {
            builder.clientAuthenticationMethods(methods.toArray(ClientAuthenticationMethod[]::new));
        }
        return builder.build();
    }

    private static TokenClaimsCustomizer compose(List<TokenClaimsCustomizer> customizers) {
        if (customizers.isEmpty()) {
            return null;
        }
        if (customizers.size() == 1) {
            return customizers.get(0);
        }
        return (claims, client, scopes) -> customizers.forEach(c -> c.customize(claims, client, scopes));
    }

    /**
     * 与端点过滤器相同的精确匹配规则（去掉 context path 后比较）
     */
    private static RequestMatcher pathMatcher(Set<String> paths) {
        return request -> paths.contains(request.getRequestURI().substring(request.getContextPath().length()));
    }

    static class AnyEndpointEnabled extends AnyNestedCondition {
        AnyEndpointEnabled() {
            super(ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = AuthorizationServerProperties.PREFIX + ".token", name = "enabled",
                havingValue = "true", matchIfMissing = true)
        static class TokenEnabled {
        }

        @ConditionalOnProperty(prefix = AuthorizationServerProperties.PREFIX + ".jwks", name = "enabled",
                havingValue = "true", matchIfMissing = true)
        static class JwksEnabled {
        }
    }
}
