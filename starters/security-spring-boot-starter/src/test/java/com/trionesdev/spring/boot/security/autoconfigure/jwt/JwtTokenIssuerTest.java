package com.trionesdev.spring.boot.security.autoconfigure.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.trionesdev.commons.core.jwt.JwtClaims;
import com.trionesdev.commons.core.jwt.JwtConfig;
import com.trionesdev.commons.core.jwt.JwtFacade;
import com.trionesdev.commons.model.AuthToken;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenIssuerTest {
    private static final int EXPIRATION_SECONDS = 1800;

    private final JwtFacade jwtFacade = new JwtFacade(JwtConfig.builder()
            .algorithm(JWSAlgorithm.HS256)
            .secret("0123456789abcdef0123456789abcdef")
            .expiration(EXPIRATION_SECONDS)
            .build(), null, List.of());
    private final JwtTokenIssuer issuer = new JwtTokenIssuer(jwtFacade);

    /**
     * 20 分钟前签发、1 分钟后过期的令牌
     */
    private String nearlyExpiredToken() {
        Instant now = Instant.now();
        return jwtFacade.generate(new JWTClaimsSet.Builder()
                .subject("user-1")
                .claim("role", "ADMIN")
                .claim("tenantId", "t-1")
                .jwtID("old-jti")
                .issueTime(Date.from(now.minus(Duration.ofMinutes(20))))
                .notBeforeTime(Date.from(now.minus(Duration.ofMinutes(20))))
                .expirationTime(Date.from(now.plus(Duration.ofMinutes(1))))
                .build());
    }

    @Test
    void refreshExtendsExpirationFromNow() {
        String old = nearlyExpiredToken();
        JWTClaimsSet oldClaims = jwtFacade.parse(old);
        Instant before = Instant.now().minusSeconds(1);

        AuthToken refreshed = issuer.refresh(old);
        assertEquals(refreshed.getAccessToken(), refreshed.getRefreshToken());
        JWTClaimsSet claims = jwtFacade.parse(refreshed.getAccessToken());

        // 有效期从刷新时刻重新计算
        assertTrue(claims.getIssueTime().toInstant().isAfter(before));
        assertTrue(claims.getExpirationTime().after(oldClaims.getExpirationTime()));
        long ttl = Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()).toSeconds();
        assertEquals(EXPIRATION_SECONDS, ttl);

        // 身份与业务声明沿用，时间类声明与 jti 不沿用
        assertEquals("user-1", claims.getSubject());
        assertEquals("ADMIN", claims.getClaim("role"));
        assertEquals("t-1", claims.getClaim("tenantId"));
        assertNull(claims.getNotBeforeTime());
        assertNull(claims.getJWTID());
    }

    @Test
    void refreshedTokenCanBeRefreshedAgain() {
        AuthToken first = issuer.refresh(nearlyExpiredToken());
        AuthToken second = issuer.refresh(first.getRefreshToken());
        assertEquals("user-1", jwtFacade.parse(second.getAccessToken()).getSubject());
    }

    @Test
    void expiredTokenCannotBeRefreshed() {
        Instant now = Instant.now();
        String expired = jwtFacade.generate(new JWTClaimsSet.Builder()
                .subject("user-1")
                .issueTime(Date.from(now.minus(Duration.ofHours(1))))
                .expirationTime(Date.from(now.minus(Duration.ofMinutes(1))))
                .build());
        assertThrows(IllegalArgumentException.class, () -> issuer.refresh(expired));
    }

    @Test
    void tokenSignedWithAnotherKeyCannotBeRefreshed() {
        JwtFacade other = new JwtFacade(JwtConfig.builder()
                .algorithm(JWSAlgorithm.HS256)
                .secret("another-secret-another-secret-00")
                .expiration(EXPIRATION_SECONDS)
                .build(), null, List.of());
        String foreign = new JwtTokenIssuer(other).issue("user-1", new JwtClaims()).getAccessToken();
        assertThrows(IllegalArgumentException.class, () -> issuer.refresh(foreign));
    }
}
