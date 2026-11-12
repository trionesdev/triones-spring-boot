package com.trionesdev.spring.boot.security.autoconfigure;

import com.trionesdev.spring.security.SecurityHttpAuthorizeRule;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

import java.util.List;

public class SecurityUtils {
    public static void configureAuthorization(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry authorizeHttpRequests,
            List<SecurityHttpAuthorizeRule> rules
    ) {

        if (CollectionUtils.isNotEmpty(rules)) {
            rules.forEach(rule -> {
                switch (rule.getAction()) {
                    case PERMIT_ALL ->
                            authorizeHttpRequests.requestMatchers(rule.getMethod(), rule.getMatchers()).permitAll();
                    case DENY_ALL ->
                            authorizeHttpRequests.requestMatchers(rule.getMethod(), rule.getMatchers()).denyAll();
                    case AUTHENTICATED ->
                            authorizeHttpRequests.requestMatchers(rule.getMethod(), rule.getMatchers()).authenticated();
                    case ANONYMOUS ->
                            authorizeHttpRequests.requestMatchers(rule.getMethod(), rule.getMatchers()).anonymous();
                    case HAS_ROLE -> {
                        if (CollectionUtils.isNotEmpty(rule.getRoles())) {
                            if (rule.getRoles().size() > 1) {
                                throw new IllegalArgumentException("Only one role is allowed");
                            }
                            authorizeHttpRequests.requestMatchers(rule.getMethod(), rule.getMatchers()).hasRole(rule.getRoles().get(0));
                        }
                    }
                    case HAS_ANY_ROLE -> {
                        if (CollectionUtils.isNotEmpty(rule.getRoles())) {
                            authorizeHttpRequests.requestMatchers(rule.getMethod(), rule.getMatchers()).hasAnyRole(rule.getRoles().toArray(String[]::new));
                        }
                    }
                    case HAS_AUTHORITY -> {
                        if (CollectionUtils.isNotEmpty(rule.getAuthorities())) {
                            if (rule.getAuthorities().size() > 1) {
                                throw new IllegalArgumentException("Only one authority is allowed");
                            }
                            authorizeHttpRequests.requestMatchers(rule.getMethod(), rule.getMatchers()).hasAuthority(rule.getAuthorities().get(0));
                        }
                    }
                    case HAS_ANY_AUTHORITY -> {
                        if (CollectionUtils.isNotEmpty(rule.getAuthorities())) {
                            authorizeHttpRequests.requestMatchers(rule.getMethod(), rule.getMatchers()).hasAnyAuthority(rule.getAuthorities().toArray(String[]::new));
                        }
                    }
                }
            });
        }
    }
}
