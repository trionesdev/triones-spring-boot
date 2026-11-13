package com.trionesdev.spring.boot.security.oauth2.server.authorization.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 启动时把 {@code triones.security.oauth2.authorization-server.default-client} 写入 {@link RegisteredClientRepository}。
 * <ul>
 *     <li>已存在同 client-id 的客户端时不覆盖（库中数据为准），只在缺少 refresh_token 授权类型时告警；</li>
 *     <li>client-secret 为明文时用 {@link PasswordEncoder} 编码后入库，带 {@code {id}} 前缀时按原值入库；</li>
 *     <li>client-secret 为空表示公共客户端：认证方式为 none 且强制 PKCE，不允许 client_credentials。</li>
 * </ul>
 * 在所有单例初始化完成后、Web 服务器启动前执行，首个请求到达时客户端已存在。
 */
@Slf4j
public class DefaultClientRegistrar implements SmartInitializingSingleton {
    private static final String PREFIX = "triones.security.oauth2.authorization-server.default-client.";

    private final AuthorizationServerProperties.Client client;
    private final RegisteredClientRepository registeredClientRepository;
    private final PasswordEncoder passwordEncoder;

    public DefaultClientRegistrar(AuthorizationServerProperties.Client client,
                                  RegisteredClientRepository registeredClientRepository,
                                  PasswordEncoder passwordEncoder) {
        this.client = Objects.requireNonNull(client, "client");
        this.registeredClientRepository = Objects.requireNonNull(registeredClientRepository, "registeredClientRepository");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
    }

    @Override
    public void afterSingletonsInstantiated() {
        RegisteredClient existing = registeredClientRepository.findByClientId(client.getClientId());
        if (existing != null) {
            if (!existing.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
                log.warn("OAuth2 client '{}' is already registered without the refresh_token grant type; "
                        + "the embedded token issuer cannot issue refresh tokens for it", client.getClientId());
            }
            log.info("OAuth2 client '{}' already registered, {}* not applied", client.getClientId(), PREFIX);
            return;
        }
        registeredClientRepository.save(toRegisteredClient());
        log.info("Registered OAuth2 client '{}'", client.getClientId());
    }

    RegisteredClient toRegisteredClient() {
        String clientId = client.getClientId();
        boolean publicClient = StringUtils.isBlank(client.getClientSecret());
        Set<AuthorizationGrantType> grantTypes = new LinkedHashSet<>();
        client.getAuthorizationGrantTypes().stream().filter(StringUtils::isNotBlank)
                .map(value -> new AuthorizationGrantType(value.trim()))
                .forEach(grantTypes::add);
        if (grantTypes.isEmpty()) {
            throw new IllegalStateException(PREFIX + "authorization-grant-types must not be empty");
        }
        if (publicClient && grantTypes.contains(AuthorizationGrantType.CLIENT_CREDENTIALS)) {
            throw new IllegalStateException(PREFIX + "client-secret is required for the client_credentials grant "
                    + "(a client without secret is a public client)");
        }

        Set<ClientAuthenticationMethod> methods = new LinkedHashSet<>();
        if (publicClient) {
            methods.add(ClientAuthenticationMethod.NONE);
        } else if (client.getClientAuthenticationMethods().isEmpty()) {
            methods.add(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
            methods.add(ClientAuthenticationMethod.CLIENT_SECRET_POST);
        } else {
            client.getClientAuthenticationMethods().stream().filter(StringUtils::isNotBlank)
                    .map(value -> new ClientAuthenticationMethod(value.trim().replace('-', '_').toLowerCase(Locale.ROOT)))
                    .forEach(methods::add);
        }

        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientIdIssuedAt(Instant.now())
                .clientName(StringUtils.defaultIfBlank(client.getClientName(), clientId))
                .clientAuthenticationMethods(set -> set.addAll(methods))
                .authorizationGrantTypes(set -> set.addAll(grantTypes))
                .scopes(set -> set.addAll(client.getScopes()))
                .redirectUris(set -> set.addAll(client.getRedirectUris()))
                .postLogoutRedirectUris(set -> set.addAll(client.getPostLogoutRedirectUris()))
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(publicClient || client.isRequireProofKey())
                        .requireAuthorizationConsent(client.isRequireAuthorizationConsent())
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(client.getAccessTokenTtl())
                        .refreshTokenTimeToLive(client.getRefreshTokenTtl())
                        .reuseRefreshTokens(client.isReuseRefreshTokens())
                        .build());
        if (!publicClient) {
            builder.clientSecret(encodeSecret(client.getClientSecret()));
        }
        return builder.build();
    }

    /**
     * 带 {@code {bcrypt}} 等前缀视为已编码；否则用容器的 PasswordEncoder（未注册时为 DelegatingPasswordEncoder）编码，
     * 与授权服务器校验 client secret 时使用的编码器一致。
     */
    private String encodeSecret(String secret) {
        return secret.startsWith("{") && secret.indexOf('}') > 1 ? secret : passwordEncoder.encode(secret);
    }
}
