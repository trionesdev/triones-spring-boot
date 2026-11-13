package com.trionesdev.spring.boot.security.autoconfigure.jwt;

import com.trionesdev.spring.security.TokenIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenIssuerConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JwtFacadeConfiguration.class))
            .withPropertyValues("triones.security.jwt.algorithm=HS256", "triones.security.jwt.secret=secret");

    @Test
    void jwtModeByDefault() {
        runner.run(context -> assertInstanceOf(JwtTokenIssuer.class, context.getBean(TokenIssuer.class)));
    }

    @Test
    void oauth2ModeLeavesTokenIssuerToAuthorizationServer() {
        // oauth2 模式下 JwtFacade 签发的令牌无法通过资源服务器（JWKSource 密钥）校验
        runner.withPropertyValues("triones.security.authorization.mode=oauth2")
                .run(context -> assertTrue(context.getBeansOfType(TokenIssuer.class).isEmpty()));
    }
}
