package com.trionesdev.spring.boot.security.oauth2.autoconfigure;

import com.trionesdev.commons.context.actor.ActorContext;
import com.trionesdev.spring.boot.security.autoconfigure.SecurityProperties;
import com.trionesdev.spring.boot.security.autoconfigure.SecurityUtils;
import com.trionesdev.spring.security.ActorAuthoritiesResolver;
import com.trionesdev.spring.security.DefaultActorAuthoritiesResolver;
import com.trionesdev.spring.security.jwt.DefaultJwtActorResolver;
import com.trionesdev.spring.security.jwt.JwtActorResolver;
import com.trionesdev.spring.security.jwt.JwtAuthenticationEntryPoint;
import com.trionesdev.spring.security.oauth2.ActorContextFilter;
import com.trionesdev.spring.security.oauth2.ActorJwtAuthenticationConverter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.ArrayList;
import java.util.List;


@RequiredArgsConstructor
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(HttpSecurity.class)
@EnableWebSecurity
@EnableConfigurationProperties(value = {
        Oauth2Properties.class
})
public class Oauth2SecurityAutoConfiguration {
    private final SecurityProperties securityProperties;
    private final ObjectProvider<JwtActorResolver> jwtActorResolverProvider;
    private final ObjectProvider<ActorAuthoritiesResolver> authoritiesResolverProvider;


    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    @ConditionalOnProperty(value = "triones.security.authorization.mode", havingValue = "oauth2", matchIfMissing = false)
    public SecurityFilterChain securityWebFilterChain(HttpSecurity http, ObjectProvider<JwtDecoder> jwtDecoderProvider, ActorContext actorContext) throws Exception {
        var jwtDecoder = jwtDecoderProvider.getIfAvailable();
        List<String> ignoredMatchers = new ArrayList<>(securityProperties.getDefaultIgnoredMatchers());
        ignoredMatchers.addAll(securityProperties.getIgnoredMatchers());

        var jwtAuthenticationConverter = new ActorJwtAuthenticationConverter(
                jwtActorResolverProvider.getIfAvailable(DefaultJwtActorResolver::new),
                authoritiesResolverProvider.getIfAvailable(DefaultActorAuthoritiesResolver::new));
        var authenticationEntryPoint = new JwtAuthenticationEntryPoint();

        if (BooleanUtils.isFalse(securityProperties.isCsrf())) {
            http.csrf(AbstractHttpConfigurer::disable);
        }
        if (BooleanUtils.isFalse(securityProperties.isAnonymous())) {
            http.anonymous(AbstractHttpConfigurer::disable);
        }
        http.authorizeHttpRequests(authorizeRequests -> {
                    authorizeRequests.requestMatchers(ignoredMatchers.toArray(String[]::new)).permitAll();
                    SecurityUtils.configureAuthorization(authorizeRequests, securityProperties.getRules());
                    authorizeRequests.anyRequest().authenticated();
                })
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(ignoringBearerTokenResolver(ignoredMatchers))
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint))
                .addFilterAfter(new ActorContextFilter(actorContext), BearerTokenAuthenticationFilter.class)
                .sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptionHandling -> exceptionHandling.authenticationEntryPoint(authenticationEntryPoint));
        return http.build();
    }

    /**
     * 放行路径不解析令牌：登录等接口即使带了过期的旧令牌也不会被 401 拦截
     */
    private static BearerTokenResolver ignoringBearerTokenResolver(List<String> ignoredMatchers) {
        var delegate = new DefaultBearerTokenResolver();
        if (ignoredMatchers.isEmpty()) {
            return delegate;
        }
        var pathMatcher = PathPatternRequestMatcher.withDefaults();
        RequestMatcher ignored = new OrRequestMatcher(ignoredMatchers.stream()
                .map(pathMatcher::matcher)
                .map(RequestMatcher.class::cast)
                .toList());
        return request -> ignored.matches(request) ? null : delegate.resolve(request);
    }
}
