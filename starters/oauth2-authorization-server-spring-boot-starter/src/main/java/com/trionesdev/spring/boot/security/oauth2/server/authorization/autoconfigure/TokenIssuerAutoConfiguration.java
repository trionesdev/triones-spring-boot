package com.trionesdev.spring.boot.security.oauth2.server.authorization.autoconfigure;

import com.trionesdev.spring.security.TokenIssuer;
import com.trionesdev.spring.security.oauth2.server.authorization.EmbeddedOauth2TokenIssuer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/**
 * oauth2 模式下由嵌入的授权服务器签发平台登录令牌（{@link EmbeddedOauth2TokenIssuer}），
 * 与资源服务器链共用 {@code JWKSource} 的签名密钥。
 * <p>
 * 位于 security starter 的 {@code JwtFacadeConfiguration} 之前，保证其 {@code jwtTokenIssuer}
 * 的 {@code @ConditionalOnMissingBean} 能看到这里的 Bean。
 */
@AutoConfiguration(after = Oauth2AuthorizationServerConfiguration.class,
        beforeName = "com.trionesdev.spring.boot.security.autoconfigure.jwt.JwtFacadeConfiguration")
@ConditionalOnProperty(value = "triones.security.authorization.mode", havingValue = "oauth2")
@ConditionalOnBean(RegisteredClientRepository.class)
public class TokenIssuerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TokenIssuer.class)
    @ConditionalOnProperty(value = "triones.security.oauth2.authorization-server.mode", havingValue = "embedded",
            matchIfMissing = true)
    public TokenIssuer embeddedTokenIssuer(AuthorizationServerProperties properties,
                                           AuthorizationServerSettings authorizationServerSettings,
                                           RegisteredClientRepository registeredClientRepository,
                                           OAuth2AuthorizationService authorizationService,
                                           OAuth2TokenGenerator<OAuth2Token> tokenGenerator) {
        String clientId = properties.getDefaultClient().getClientId();
        if (StringUtils.isBlank(clientId)) {
            throw new IllegalStateException("triones.security.oauth2.authorization-server.default-client.client-id "
                    + "is required: the embedded token issuer signs platform logins with this client");
        }
        return new EmbeddedOauth2TokenIssuer(clientId, authorizationServerSettings, registeredClientRepository,
                authorizationService, tokenGenerator);
    }

}
