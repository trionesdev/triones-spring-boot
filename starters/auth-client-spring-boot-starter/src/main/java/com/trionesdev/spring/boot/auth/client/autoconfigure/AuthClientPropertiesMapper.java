package com.trionesdev.spring.boot.auth.client.autoconfigure;

import com.trionesdev.spring.security.auth.client.ClientRegistration;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 把 {@link AuthClientProperties} 转换为 {@link ClientRegistration}。
 */
public final class AuthClientPropertiesMapper {
    static final String CLIENT_CREDENTIALS = "client_credentials";

    private AuthClientPropertiesMapper() {
    }

    public static List<ClientRegistration> toRegistrations(AuthClientProperties properties) {
        List<ClientRegistration> result = new ArrayList<>();
        Map<String, AuthClientProperties.Registration> registrations = properties.getRegistration();
        if (registrations == null) {
            return result;
        }
        registrations.forEach((id, registration) -> result.add(toRegistration(id, registration, properties)));
        return result;
    }

    static ClientRegistration toRegistration(String registrationId, AuthClientProperties.Registration registration,
                                             AuthClientProperties properties) {
        String prefix = "triones.security.auth.client.registration." + registrationId;
        String grantType = registration.getAuthorizationGrantType();
        if (StringUtils.hasText(grantType) && !CLIENT_CREDENTIALS.equalsIgnoreCase(grantType.trim())) {
            throw new IllegalStateException(prefix + ".authorization-grant-type: unsupported '" + grantType
                    + "', only client_credentials is supported");
        }
        if (!StringUtils.hasText(registration.getClientId())) {
            throw new IllegalStateException(prefix + ".client-id must not be empty");
        }

        String providerId = StringUtils.hasText(registration.getProvider()) ? registration.getProvider() : registrationId;
        AuthClientProperties.Provider provider = properties.getProvider() == null ? null
                : properties.getProvider().get(providerId);
        if (provider == null || !StringUtils.hasText(provider.getTokenUri())) {
            throw new IllegalStateException("triones.security.auth.client.provider." + providerId
                    + ".token-uri must be configured for registration '" + registrationId + "'");
        }

        return ClientRegistration.builder()
                .registrationId(registrationId)
                .clientId(registration.getClientId())
                .clientSecret(registration.getClientSecret())
                .tokenUri(provider.getTokenUri())
                .scopes(registration.getScope() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(registration.getScope()))
                .clientAuthenticationMethod(toAuthenticationMethod(prefix, registration.getClientAuthenticationMethod()))
                .build();
    }

    private static ClientRegistration.ClientAuthenticationMethod toAuthenticationMethod(String prefix, String value) {
        if (!StringUtils.hasText(value)) {
            return ClientRegistration.ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return ClientRegistration.ClientAuthenticationMethod.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(prefix + ".client-authentication-method: unsupported '" + value
                    + "', expected client_secret_basic or client_secret_post");
        }
    }
}
