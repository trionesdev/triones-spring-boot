package com.trionesdev.spring.boot.security.autoconfigure.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.trionesdev.commons.core.jwt.JwtConfig;
import com.trionesdev.commons.core.jwt.JwtFacade;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;

@AutoConfiguration
@ConditionalOnClass(JwtFacade.class)
@EnableConfigurationProperties(JwtSecurityProperties.class)
public class JwtFacadeConfiguration {
    @Bean
    @ConditionalOnMissingBean(JwtFacade.class)
    public JwtFacade jwtFacade(JwtSecurityProperties properties) {
        JWSAlgorithm algorithm = properties.getAlgorithm();
        if (properties.getExpiration() <= 0) {
            throw new IllegalArgumentException("triones.security.jwt.expiration must be positive");
        }
        if (algorithm == null || !(JWSAlgorithm.Family.RSA.contains(algorithm)
                || JWSAlgorithm.ES256.equals(algorithm) || JWSAlgorithm.ES384.equals(algorithm)
                || JWSAlgorithm.ES512.equals(algorithm) || JWSAlgorithm.Family.HMAC_SHA.contains(algorithm))) {
            throw new IllegalArgumentException("Unsupported JWT algorithm: " + algorithm);
        }
        if (JWSAlgorithm.Family.HMAC_SHA.contains(algorithm)) {
            int bytes = JWSAlgorithm.HS256.equals(algorithm) ? 32
                    : JWSAlgorithm.HS384.equals(algorithm) ? 48 : 64;
            if (properties.getSecret() == null) {
                throw new IllegalArgumentException(algorithm + " secret is required");
            }
            if (properties.getSecret().getBytes(StandardCharsets.UTF_8).length > bytes) {
                throw new IllegalArgumentException(algorithm + " secret must not exceed " + bytes + " UTF-8 bytes");
            }
            // JwtFacade pads shorter UTF-8 key bytes with trailing zero bytes.
        }
        return new JwtFacade(JwtConfig.builder()
                .algorithm(algorithm).secret(properties.getSecret())
                .expiration(properties.getExpiration()).build());
    }
}
