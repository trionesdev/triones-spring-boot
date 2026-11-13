package com.trionesdev.spring.boot.security.autoconfigure.jwt;

import com.nimbusds.jwt.JWTClaimNames;
import com.nimbusds.jwt.JWTClaimsSet;
import com.trionesdev.commons.core.jwt.JwtClaims;
import com.trionesdev.commons.core.jwt.JwtFacade;
import com.trionesdev.commons.model.AuthToken;
import com.trionesdev.spring.security.TokenIssuer;
import lombok.AllArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用 {@link JwtFacade} 签发令牌：access_token 与 refresh_token 是同一个 JWT。
 */
@AllArgsConstructor
public class JwtTokenIssuer implements TokenIssuer {
    /**
     * 刷新时重新计算的时间类声明；jti 标识单个令牌，新令牌不沿用
     */
    private static final List<String> REISSUED_CLAIMS = List.of(JWTClaimNames.ISSUED_AT,
            JWTClaimNames.EXPIRATION_TIME, JWTClaimNames.NOT_BEFORE, JWTClaimNames.JWT_ID, JWTClaimNames.SUBJECT);

    private final JwtFacade jwtFacade;

    @Override
    public AuthToken issue(String subject, JwtClaims claims) {
        String token = jwtFacade.generate(subject, claims);
        return AuthToken.builder().accessToken(token).refreshToken(token).build();
    }

    /**
     * 校验令牌（签名、未过期）后签发新令牌：沿用 sub 与业务声明，iat 为当前时间，
     * exp 为当前时间 + {@code triones.security.jwt.expiration}，即从刷新时刻起延长有效期。
     *
     * @throws IllegalArgumentException 令牌非法、签名无效或已过期
     */
    @Override
    public AuthToken refresh(String refreshTokenValue) {
        JWTClaimsSet current = jwtFacade.parse(refreshTokenValue);
        Map<String, Object> claims = new LinkedHashMap<>(current.getClaims());
        REISSUED_CLAIMS.forEach(claims::remove);
        String token = jwtFacade.generate(current.getSubject(), claims);
        return AuthToken.builder().accessToken(token).refreshToken(token).build();
    }
}
