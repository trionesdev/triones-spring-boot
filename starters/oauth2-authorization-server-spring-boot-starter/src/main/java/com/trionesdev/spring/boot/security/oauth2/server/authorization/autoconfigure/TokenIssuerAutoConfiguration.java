package com.trionesdev.spring.boot.security.oauth2.server.authorization.autoconfigure;

import com.trionesdev.spring.security.TokenIssuer;
import com.trionesdev.spring.security.oauth2.server.authorization.EmbeddedOauth2TokenIssuer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

@RequiredArgsConstructor
@Configuration
@ConditionalOnProperty(value = "triones.security.authorization.mode", havingValue = "oauth2", matchIfMissing = false)
@EnableConfigurationProperties(AuthorizationServerProperties.class)
public class TokenIssuerAutoConfiguration {

    private final AuthorizationServerProperties authorizationServerProperties;


    @Bean
    public TokenIssuer embeddedTokenIssuer(
            ObjectProvider<AuthorizationServerSettings> authorizationServerSettingsProvider,
            ObjectProvider<RegisteredClientRepository> registeredClientRepositoryProvider,
            ObjectProvider<OAuth2AuthorizationService> authorizationServiceProvider,
            ObjectProvider<OAuth2TokenGenerator<OAuth2Token>> tokenGeneratorProvider
    ) {
        String clientId = authorizationServerProperties.getDefaultClient().getClientId();
        AuthorizationServerSettings authorizationServerSettings = authorizationServerSettingsProvider.getIfAvailable();
        RegisteredClientRepository registeredClientRepository = registeredClientRepositoryProvider.getIfAvailable();
        OAuth2AuthorizationService authorizationService = authorizationServiceProvider.getIfAvailable();
        OAuth2TokenGenerator<OAuth2Token> tokenGenerator = tokenGeneratorProvider.getIfAvailable();
        return new EmbeddedOauth2TokenIssuer(clientId, authorizationServerSettings, registeredClientRepository, authorizationService, tokenGenerator);
    }

}
