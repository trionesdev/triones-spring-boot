package com.trionesdev.spring.boot.security.autoconfigure.jwt;

import com.trionesdev.commons.context.actor.ActorContext;
import com.trionesdev.commons.core.jwt.JwtFacade;
import com.trionesdev.spring.boot.security.autoconfigure.SecurityConfiguration;
import com.trionesdev.spring.boot.security.autoconfigure.SecurityProperties;
import com.trionesdev.spring.boot.security.autoconfigure.SecurityUtils;
import com.trionesdev.spring.security.ActorAuthoritiesResolver;
import com.trionesdev.spring.security.jwt.JwtActorResolver;
import com.trionesdev.spring.security.jwt.JwtAuthenticationEntryPoint;
import com.trionesdev.spring.security.jwt.JwtAuthenticationFilter;
import com.trionesdev.spring.security.jwt.JwtServerConfigurer;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@RequiredArgsConstructor
@AutoConfiguration(after = {SecurityConfiguration.class, JwtFacadeConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({HttpSecurity.class, JwtAuthenticationFilter.class})
@EnableConfigurationProperties(value = {
        JwtSecurityProperties.class
})
public class JwtSecurityConfiguration {
    private final ApplicationContext applicationContext;
    private final SecurityProperties securityProperties;
    private final ObjectProvider<JwtActorResolver> jwtActorResolverProvider;
    private final ObjectProvider<ActorAuthoritiesResolver> authoritiesResolverObjectProvider;

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain securityWebFilterChain(HttpSecurity http, JwtFacade jwtFacade, ActorContext actorContext) throws Exception {

        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtFacade, actorContext,
                jwtActorResolverProvider.getIfAvailable(),
                authoritiesResolverObjectProvider.getIfAvailable());
        JwtServerConfigurer<HttpSecurity> jwtServerConfigurer = new JwtServerConfigurer<>(applicationContext, jwtAuthenticationFilter);

        List<String> ignoreMatcherList = securityProperties.getDefaultIgnoredMatchers();
        ignoreMatcherList.addAll(securityProperties.getIgnoredMatchers());

        if (BooleanUtils.isFalse(securityProperties.isCsrf())) {
            http.csrf(AbstractHttpConfigurer::disable);
        }
        if (BooleanUtils.isFalse(securityProperties.isAnonymous())) {
            http.anonymous(AbstractHttpConfigurer::disable);
        }
        http.authorizeHttpRequests(authorizeRequests ->
                        {
                            authorizeRequests.requestMatchers(ignoreMatcherList.toArray(String[]::new)).permitAll();
                            SecurityUtils.configureAuthorization(authorizeRequests, securityProperties.getRules());
                            authorizeRequests.anyRequest().authenticated();
                        }
                )
                .with(jwtServerConfigurer, Customizer.withDefaults())
                .sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptionHandling -> exceptionHandling.authenticationEntryPoint(new JwtAuthenticationEntryPoint()))
        ;
        return http.build();
    }

}
