package com.trionesdev.spring.boot.security.autoconfigure.jwt;

import com.google.common.collect.Lists;
import com.nimbusds.jose.JOSEException;
import com.trionesdev.commons.context.actor.ActorContext;
import com.trionesdev.commons.core.jwt.JwtConfig;
import com.trionesdev.commons.core.jwt.JwtFacade;
import com.trionesdev.commons.core.jwt.JwtTokenFacade;
import com.trionesdev.spring.boot.security.autoconfigure.SecurityProperties;
import com.trionesdev.spring.security.ActorAuthoritiesResolver;
import com.trionesdev.spring.security.ResourceServerConfig;
import com.trionesdev.spring.security.jwt.JwtActorResolver;
import com.trionesdev.spring.security.jwt.JwtAuthenticationEntryPoint;
import com.trionesdev.spring.security.jwt.JwtAuthenticationFilter;
import com.trionesdev.spring.security.jwt.JwtServerConfigurer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;
import java.util.List;

@RequiredArgsConstructor
@Configuration
@EnableConfigurationProperties(value = {
        JwtSecurityProperties.class
})
public class JwtSecurityConfiguration {
    private final ApplicationContext applicationContext;
    private final ActorContext actorContext;
    private final SecurityProperties securityProperties;
    private final JwtSecurityProperties jwtSecurityProperties;
    private final ObjectProvider<JwtActorResolver> jwtActorResolverProvider;
    private final ObjectProvider<ActorAuthoritiesResolver> authoritiesResolverObjectProvider;

    private final String[] ignoreMatchers = {"/favicon.ico", "/v3/api-docs/**", "/v2/api-docs", "/webjars/**", "/swagger-resources/**",
            "/swagger-ui/**", "/swagger-ui.html", "/actuator/**", "/websocket/**"};

    @Bean
    public JwtFacade jwtTokenFacade() {
        JwtConfig config = JwtConfig.builder()
                .expiration(jwtSecurityProperties.getExpiration())
                .build();
        return new JwtFacade(config);
    }


    @Bean
    public SecurityFilterChain securityWebFilterChain(HttpSecurity http, JwtFacade jwtFacade) throws Exception {
        SecurityProperties.ResourceServer.Jwt jwtProps = securityProperties.getResourceServer().getJwt();
        ResourceServerConfig.Jwt jwtConfig = ResourceServerConfig.Jwt.builder().issuerUri(jwtProps.getIssuerUri())
                .jwkSetUri(jwtProps.getJwkSetUri()).tokenUri(jwtProps.getTokenUri())
                .build();
        ResourceServerConfig resourceServerConfig = ResourceServerConfig.builder()
                .enabled(securityProperties.getResourceServer().getEnabled())
                .jwt(jwtConfig).build();
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(resourceServerConfig, jwtFacade, actorContext,
                jwtActorResolverProvider.getIfAvailable(),
                authoritiesResolverObjectProvider.getIfAvailable());
        JwtServerConfigurer<HttpSecurity> jwtServerConfigurer = new JwtServerConfigurer<>(applicationContext, jwtAuthenticationFilter);

        List<String> ignoreMatcherList = Lists.newArrayList(ignoreMatchers);
        ignoreMatcherList.addAll(Lists.newArrayList(securityProperties.getIgnoreMatchers()));

        http.csrf(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                .requestMatchers(ignoreMatcherList.toArray(new String[ignoreMatcherList.size()])).permitAll()
                                .requestMatchers(securityProperties.getExcludeMatchers()).permitAll()
                                .requestMatchers(HttpMethod.GET, securityProperties.getExcludeGetMatchers()).permitAll()
                                .requestMatchers(HttpMethod.POST, securityProperties.getExcludePostMatchers()).permitAll()
                                .requestMatchers(HttpMethod.PUT, securityProperties.getExcludePutMatchers()).permitAll()
                                .requestMatchers(HttpMethod.DELETE, securityProperties.getExcludeDeleteMatchers()).permitAll()
                                .anyRequest().authenticated()
                )
                .with(jwtServerConfigurer, httpSecurityJwtServerConfigurer -> httpSecurityJwtServerConfigurer.configure(http))
                .sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptionHandling -> exceptionHandling.authenticationEntryPoint(new JwtAuthenticationEntryPoint()))
        ;


        return http.build();
    }

}
