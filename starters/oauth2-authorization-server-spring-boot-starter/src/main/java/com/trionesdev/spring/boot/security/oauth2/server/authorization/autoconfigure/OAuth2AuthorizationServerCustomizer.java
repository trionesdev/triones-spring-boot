package com.trionesdev.spring.boot.security.oauth2.server.authorization.autoconfigure;

import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;

public interface OAuth2AuthorizationServerCustomizer {
    void customize(OAuth2AuthorizationServerConfigurer configurer);
}
