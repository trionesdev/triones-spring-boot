package com.trionesdev.spring.boot.security.autoconfigure.jwt;

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
    private Boolean enabled;
    private Boolean remote = false;
    private String issuerUri;
    private String secret = "secret1234567890qwertyuiopasdfghg";
    private int expiration = 30 * 60;
}
