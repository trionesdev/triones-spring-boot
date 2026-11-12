package com.trionesdev.spring.boot.security.autoconfigure.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.trionesdev.commons.core.jwt.JwtConfig;
import com.trionesdev.commons.core.jwt.JwtFacade;
import com.trionesdev.spring.security.TokenIssuer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@AutoConfiguration
@ConditionalOnClass(JwtFacade.class)
@EnableConfigurationProperties(JwtSecurityProperties.class)
public class JwtFacadeConfiguration {



    @Bean
    @ConditionalOnMissingBean(JwtFacade.class)
    public JwtFacade jwtFacade(JwtSecurityProperties properties) {
        if (properties.getAlgorithm() == null) {
            throw new IllegalArgumentException("triones.security.jwt.algorithm is required");
        }
        JWSAlgorithm algorithm = properties.getAlgorithm().toJwsAlgorithm();
        if (properties.getExpiration() <= 0) {
            throw new IllegalArgumentException("triones.security.jwt.expiration must be positive");
        }
        JWK signingKey = JwtSigningKeyLoader.load(properties.getKey(), algorithm);
        List<JWK> verificationKeys = JwtSigningKeyLoader.loadVerificationKeys(properties.getKey(), algorithm);
        if (!verificationKeys.isEmpty() && signingKey == null && !JWSAlgorithm.Family.HMAC_SHA.contains(algorithm)) {
            throw new IllegalArgumentException("triones.security.jwt.key.verification-keys requires a persistent "
                    + "signing key (triones.security.jwt.key.*)");
        }
        if (JWSAlgorithm.Family.HMAC_SHA.contains(algorithm) && signingKey == null) {
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
        if (signingKey == null && !JWSAlgorithm.Family.HMAC_SHA.contains(algorithm)) {
            log.warn("No persistent JWT signing key configured for {}; an ephemeral key is generated. "
                    + "Tokens become invalid after restart and cannot be verified across instances. "
                    + "Configure triones.security.jwt.key.*", algorithm);
        }
        // 密钥类型与算法不匹配时由 JwtFacade 构造时报错
        return new JwtFacade(JwtConfig.builder()
                .algorithm(algorithm).secret(properties.getSecret())
                .expiration(properties.getExpiration()).build(), signingKey, verificationKeys);
    }


    @Bean
    @ConditionalOnMissingBean(TokenIssuer.class)
    @ConditionalOnProperty(prefix = "triones.security.oauth2", name = "enabled", havingValue = "false", matchIfMissing = true)
    public TokenIssuer jwtTokenIssuer(JwtFacade jwtFacade) {
        return new JwtTokenIssuer(jwtFacade);
    }

}
