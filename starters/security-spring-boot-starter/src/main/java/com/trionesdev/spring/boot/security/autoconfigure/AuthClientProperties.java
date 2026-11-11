package com.trionesdev.spring.boot.security.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@Data
@ConfigurationProperties("triones.security.auth.client")
public class AuthClientProperties {
    private Map<String, Registration> registration;
    private Map<String, Provider> provider;

    @Data
    public static class Registration {
        private String provider;
        private String clientId;
        private String clientSecret;
        private String authorizationGrantType;
        private String clientAuthenticationMethod;
    }

    @Data
    public static class Provider {
        private String tokenUri;
    }

}
