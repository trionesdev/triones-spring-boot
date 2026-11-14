package com.trionesdev.spring.boot.security.autoconfigure.jwt;

import com.nimbusds.jose.JWSAlgorithm;

/** Supported JWT signing algorithms for configuration binding. */
public enum Algorithm {
    HS256(JWSAlgorithm.HS256),
    HS384(JWSAlgorithm.HS384),
    HS512(JWSAlgorithm.HS512),
    RS256(JWSAlgorithm.RS256),
    RS384(JWSAlgorithm.RS384),
    RS512(JWSAlgorithm.RS512),
    PS256(JWSAlgorithm.PS256),
    PS384(JWSAlgorithm.PS384),
    PS512(JWSAlgorithm.PS512),
    ES256(JWSAlgorithm.ES256),
    ES384(JWSAlgorithm.ES384),
    ES512(JWSAlgorithm.ES512);

    private final JWSAlgorithm jwsAlgorithm;

    Algorithm(JWSAlgorithm jwsAlgorithm) {
        this.jwsAlgorithm = jwsAlgorithm;
    }

    public JWSAlgorithm toJwsAlgorithm() {
        return jwsAlgorithm;
    }
}
