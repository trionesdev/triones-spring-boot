package com.trionesdev.spring.boot.security.autoconfigure.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.trionesdev.commons.core.jwt.JwtConfig;
import com.trionesdev.commons.core.jwt.JwtFacade;
import com.trionesdev.spring.security.jwks.JwksEndpointFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwksEndpointTest {

    private static JwtConfig config(JWSAlgorithm algorithm) {
        return JwtConfig.builder().algorithm(algorithm).expiration(600).build();
    }

    private static MockHttpServletResponse call(JwksEndpointFilter filter, String method, String uri,
                                                FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void servesPublicJwkSetUsableForRemoteVerification() throws Exception {
        RSAKey current = new RSAKeyGenerator(2048).keyID("new").generate();
        RSAKey previous = new RSAKeyGenerator(2048).keyID("old").generate();
        JwtFacade facade = new JwtFacade(config(JWSAlgorithm.RS256), current, List.of(previous.toPublicJWK()));
        JwksEndpointFilter filter = new JwksEndpointFilter(facade, "/oauth2/jwks", Duration.ofMinutes(5));

        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = call(filter, "GET", "/oauth2/jwks", chain);
        assertEquals(200, response.getStatus());
        assertNull(chain.getRequest(), "request must not continue to the security chain");
        assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals("public, max-age=300", response.getHeader("Cache-Control"));

        JWKSet set = JWKSet.parse(response.getContentAsString());
        assertEquals(List.of("new", "old"), set.getKeys().stream().map(JWK::getKeyID).toList());
        assertTrue(set.getKeys().stream().noneMatch(JWK::isPrivate));
        for (String field : new String[]{"\"d\"", "\"p\"", "\"q\"", "\"dp\"", "\"dq\"", "\"qi\""}) {
            assertFalse(response.getContentAsString().contains(field), field);
        }

        // 远程校验方仅凭 JWKS 即可校验 token（与 JwtAuthenticationFilter 远程模式相同的 nimbus 处理器）
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, new ImmutableJWKSet<>(set)));
        assertEquals("u", processor.process(facade.generate("u", Map.of()), null).getSubject());
        String oldToken = new JwtFacade(config(JWSAlgorithm.RS256), previous).generate("old-user", Map.of());
        assertEquals("old-user", processor.process(oldToken, null).getSubject());
    }

    @Test
    void otherPathsPassThrough() throws Exception {
        JwksEndpointFilter filter = new JwksEndpointFilter(new JwtFacade(config(JWSAlgorithm.ES256)),
                "/oauth2/jwks", Duration.ZERO);
        MockFilterChain chain = new MockFilterChain();
        call(filter, "GET", "/api/users", chain);
        assertNotNull(chain.getRequest());
    }

    @Test
    void respectsContextPathHeadAndMethods() throws Exception {
        JwksEndpointFilter filter = new JwksEndpointFilter(new JwtFacade(config(JWSAlgorithm.ES256)),
                "/.well-known/jwks.json", Duration.ZERO);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/.well-known/jwks.json");
        request.setContextPath("/app");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(200, response.getStatus());
        assertEquals("no-store", response.getHeader("Cache-Control"));

        MockHttpServletResponse head = call(filter, "HEAD", "/.well-known/jwks.json", new MockFilterChain());
        assertEquals(200, head.getStatus());
        assertEquals(0, head.getContentAsByteArray().length);
        assertTrue(head.getContentLength() > 0);

        MockHttpServletResponse post = call(filter, "POST", "/.well-known/jwks.json", new MockFilterChain());
        assertEquals(405, post.getStatus());
        assertEquals("GET, HEAD", post.getHeader("Allow"));
    }

    @Test
    void hmacCannotBePublished() {
        JwtFacade hmac = new JwtFacade(config(JWSAlgorithm.HS256));
        assertThrows(IllegalStateException.class, () -> new JwksEndpointFilter(hmac, "/oauth2/jwks", Duration.ZERO));
    }

    @Test
    void invalidPathRejected() {
        JwtFacade facade = new JwtFacade(config(JWSAlgorithm.RS256));
        assertThrows(IllegalArgumentException.class, () -> new JwksEndpointFilter(facade, "oauth2/jwks", Duration.ZERO));
    }
}
