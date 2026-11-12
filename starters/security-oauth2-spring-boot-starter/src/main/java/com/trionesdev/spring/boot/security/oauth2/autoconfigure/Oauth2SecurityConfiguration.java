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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
@Configuration
public class Oauth2SecurityConfiguration {
    private final SecurityProperties securityProperties;
    private final ObjectProvider<JwtActorResolver> jwtActorResolverProvider;
    private final ObjectProvider<ActorAuthoritiesResolver> authoritiesResolverProvider;

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public SecurityFilterChain securityWebFilterChain(HttpSecurity http, JwtDecoder jwtDecoder, ActorContext actorContext) throws Exception {
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
