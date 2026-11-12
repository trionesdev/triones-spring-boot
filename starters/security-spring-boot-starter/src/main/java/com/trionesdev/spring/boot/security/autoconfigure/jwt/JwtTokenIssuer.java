package com.trionesdev.spring.boot.security.autoconfigure.jwt;

import com.nimbusds.jwt.JWTClaimsSet;
import com.trionesdev.commons.core.jwt.JwtClaims;
import com.trionesdev.commons.core.jwt.JwtFacade;
import com.trionesdev.commons.model.AuthToken;
import com.trionesdev.spring.security.TokenIssuer;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class JwtTokenIssuer implements TokenIssuer {
    private final JwtFacade jwtFacade;

    @Override
    public AuthToken issue(String subject, JwtClaims claims) {
        String token = jwtFacade.generate(subject, claims);
        return AuthToken.builder().accessToken(token).refreshToken(token).build();
    }

    @Override
    public AuthToken refresh(String refreshTokenValue) {
        JWTClaimsSet claims = jwtFacade.parse(refreshTokenValue);
        String token = jwtFacade.generate(claims);
        return AuthToken.builder().accessToken(token).refreshToken(token).build();
    }
}
