package com.trionesdev.spring.boot.security.oauth2.server.authorization.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DefaultClientRegistrarTest {

    private static DefaultClientRegistrar registrar(AuthorizationServerProperties.Client client) {
        // InMemoryRegisteredClientRepository 要求至少一个客户端，放一个无关的占位
        var repository = new InMemoryRegisteredClientRepository(RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("placeholder").authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).build());
        return new DefaultClientRegistrar(client, repository, PasswordEncoderFactories.createDelegatingPasswordEncoder());
    }

    private static AuthorizationServerProperties.Client client(String id, String secret) {
        var client = new AuthorizationServerProperties.Client();
        client.setClientId(id);
        client.setClientSecret(secret);
        return client;
    }

    @Test
    void prefixedSecretIsStoredVerbatim() {
        RegisteredClient registered = registrar(client("a", "{noop}plain")).toRegisteredClient();
        assertEquals("{noop}plain", registered.getClientSecret());
        assertEquals(Set.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC, ClientAuthenticationMethod.CLIENT_SECRET_POST),
                registered.getClientAuthenticationMethods());
    }

    @Test
    void configuredSettingsAreApplied() {
        var client = client("a", "s");
        client.setClientName("App");
        client.setClientAuthenticationMethods(Set.of("client-secret-post"));
        client.setAuthorizationGrantTypes(Set.of("authorization_code", "refresh_token"));
        client.setRedirectUris(Set.of("https://app.example.com/callback"));
        client.setRequireProofKey(true);
        client.setReuseRefreshTokens(true);
        RegisteredClient registered = registrar(client).toRegisteredClient();
        assertEquals("App", registered.getClientName());
        assertEquals(Set.of(ClientAuthenticationMethod.CLIENT_SECRET_POST), registered.getClientAuthenticationMethods());
        assertTrue(registered.getAuthorizationGrantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE));
        assertTrue(registered.getClientSettings().isRequireProofKey());
        assertTrue(registered.getTokenSettings().isReuseRefreshTokens());
    }

    @Test
    void publicClientUsesNoneAndPkce() {
        var client = client("spa", null);
        client.setAuthorizationGrantTypes(Set.of("authorization_code"));
        client.setRedirectUris(Set.of("https://spa.example.com/callback"));
        RegisteredClient registered = registrar(client).toRegisteredClient();
        assertNull(registered.getClientSecret());
        assertEquals(Set.of(ClientAuthenticationMethod.NONE), registered.getClientAuthenticationMethods());
        assertTrue(registered.getClientSettings().isRequireProofKey());
    }

    @Test
    void publicClientCannotUseClientCredentials() {
        // 默认授权类型含 client_credentials
        assertThrows(IllegalStateException.class, () -> registrar(client("spa", "")).toRegisteredClient());
    }
}
