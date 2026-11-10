package com.trionesdev.spring.boot.security.autoconfigure.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ConfigurationProperties("triones.security.jwt")
public class JwtSecurityProperties {
    private JWSAlgorithm algorithm = JWSAlgorithm.RS256; // 签名算法
    private String secret;
    private int expiration = 30 * 60;
}
