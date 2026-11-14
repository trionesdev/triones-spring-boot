package com.trionesdev.spring.boot.security.autoconfigure.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import com.trionesdev.commons.core.jwt.JwtFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.FileSystemResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtSigningKeyLoaderTest {

    @TempDir
    static Path dir;

    static RSAKey rsaKey;
    static ECKey ecKey;
    static Path rsaPrivatePem;
    static Path rsaPublicPem;
    static Path ecPrivatePem;
    static Path ecPublicPem;

    @BeforeAll
    static void generateKeys() throws Exception {
        rsaKey = new RSAKeyGenerator(2048).generate();
        ecKey = new ECKeyGenerator(Curve.P_256).generate();
        rsaPrivatePem = writePem("rsa-private.pem", "PRIVATE KEY", rsaKey.toRSAPrivateKey().getEncoded());
        rsaPublicPem = writePem("rsa-public.pem", "PUBLIC KEY", rsaKey.toRSAPublicKey().getEncoded());
        ecPrivatePem = writePem("ec-private.pem", "PRIVATE KEY", ecKey.toECPrivateKey().getEncoded());
        ecPublicPem = writePem("ec-public.pem", "PUBLIC KEY", ecKey.toECPublicKey().getEncoded());
    }

    private static Path writePem(String name, String type, byte[] der) throws Exception {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        Path path = dir.resolve(name);
        Files.writeString(path, "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n");
        return path;
    }

    private static JwtSecurityProperties properties(Algorithm algorithm) {
        JwtSecurityProperties properties = new JwtSecurityProperties();
        properties.setAlgorithm(algorithm);
        return properties;
    }

    private static JwtFacade facade(JwtSecurityProperties properties) {
        return new JwtFacadeConfiguration().jwtFacade(properties);
    }

    /** 两个独立创建的 JwtFacade（模拟两个实例/重启前后）互相能验证 token */
    private static void assertSharedKey(JwtSecurityProperties properties) {
        String token = facade(properties).generate("user-1", Map.of("k", "v"));
        assertEquals("user-1", facade(properties).parse(token).getSubject());
    }

    private static String kid(String token) throws Exception {
        return SignedJWT.parse(token).getHeader().getKeyID();
    }

    @Test
    void rsaPkcs8PemDerivesPublicKey() throws Exception {
        JwtSecurityProperties properties = properties(Algorithm.RS256);
        properties.getKey().setPrivateKeyLocation(new FileSystemResource(rsaPrivatePem));
        assertSharedKey(properties);

        JWK jwk = JwtSigningKeyLoader.load(properties.getKey(), JWSAlgorithm.RS256);
        assertEquals(rsaKey.toRSAPublicKey(), jwk.toRSAKey().toRSAPublicKey());
        // 默认 kid = thumbprint，跨实例稳定
        assertEquals(rsaKey.computeThumbprint().toString(), jwk.getKeyID());
        assertEquals(jwk.getKeyID(), kid(facade(properties).generate("u", Map.of())));
    }

    @Test
    void rsaPemWithExplicitPublicKeyAndKeyId() throws Exception {
        JwtSecurityProperties properties = properties(Algorithm.PS256);
        properties.getKey().setPrivateKeyLocation(new FileSystemResource(rsaPrivatePem));
        properties.getKey().setPublicKeyLocation(new FileSystemResource(rsaPublicPem));
        properties.getKey().setKeyId("jwt-2026");
        assertSharedKey(properties);
        assertEquals("jwt-2026", kid(facade(properties).generate("u", Map.of())));
    }

    @Test
    void ecPemRequiresPublicKey() {
        JwtSecurityProperties properties = properties(Algorithm.ES256);
        properties.getKey().setPrivateKeyLocation(new FileSystemResource(ecPrivatePem));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> facade(properties));
        assertTrue(ex.getMessage().contains("public-key-location"));

        properties.getKey().setPublicKeyLocation(new FileSystemResource(ecPublicPem));
        assertSharedKey(properties);
    }

    @Test
    void jwkFile() throws Exception {
        Path jwkFile = dir.resolve("rsa.jwk.json");
        Files.writeString(jwkFile, rsaKey.toJSONString());
        JwtSecurityProperties properties = properties(Algorithm.RS256);
        properties.getKey().setJwkLocation(new FileSystemResource(jwkFile));
        assertSharedKey(properties);
    }

    @Test
    void publicOnlyJwkRejected() throws Exception {
        Path jwkFile = dir.resolve("rsa-public.jwk.json");
        Files.writeString(jwkFile, rsaKey.toPublicJWK().toJSONString());
        JwtSecurityProperties properties = properties(Algorithm.RS256);
        properties.getKey().setJwkLocation(new FileSystemResource(jwkFile));
        assertThrows(IllegalArgumentException.class, () -> facade(properties));
    }

    @Test
    void pkcs12KeyStore() throws Exception {
        Path keyStore = dir.resolve("jwt.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process process = new ProcessBuilder(keytool, "-genkeypair", "-keyalg", "RSA", "-keysize", "2048",
                "-alias", "jwt", "-dname", "CN=test", "-validity", "1",
                "-storetype", "PKCS12", "-keystore", keyStore.toString(), "-storepass", "changeit")
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);

        JwtSecurityProperties properties = properties(Algorithm.RS256);
        properties.getKey().getKeyStore().setLocation(new FileSystemResource(keyStore));
        properties.getKey().getKeyStore().setPassword("changeit");
        // 只有一个私钥条目，alias 可省略
        assertSharedKey(properties);

        properties.getKey().getKeyStore().setAlias("missing");
        assertThrows(IllegalArgumentException.class, () -> facade(properties));
    }

    @Test
    void multipleSourcesRejected() throws Exception {
        Path jwkFile = dir.resolve("multi.jwk.json");
        Files.writeString(jwkFile, rsaKey.toJSONString());
        JwtSecurityProperties properties = properties(Algorithm.RS256);
        properties.getKey().setJwkLocation(new FileSystemResource(jwkFile));
        properties.getKey().setPrivateKeyLocation(new FileSystemResource(rsaPrivatePem));
        assertThrows(IllegalArgumentException.class, () -> facade(properties));
    }

    @Test
    void pkcs1PemRejectedWithHint() throws Exception {
        Path pkcs1 = dir.resolve("pkcs1.pem");
        Files.writeString(pkcs1, "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----\n");
        JwtSecurityProperties properties = properties(Algorithm.RS256);
        properties.getKey().setPrivateKeyLocation(new FileSystemResource(pkcs1));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> facade(properties));
        assertTrue(ex.getMessage().contains("openssl pkcs8"));
    }

    @Test
    void keyTypeMustMatchAlgorithm() {
        JwtSecurityProperties properties = properties(Algorithm.ES256);
        properties.getKey().setPrivateKeyLocation(new FileSystemResource(rsaPrivatePem));
        assertThrows(IllegalArgumentException.class, () -> facade(properties));
    }

    @Test
    void hmacRejectsPemKey() {
        JwtSecurityProperties properties = properties(Algorithm.HS256);
        properties.setSecret("secret");
        properties.getKey().setPrivateKeyLocation(new FileSystemResource(rsaPrivatePem));
        assertThrows(IllegalArgumentException.class, () -> facade(properties));
    }

    @Test
    void missingResourceRejected() {
        JwtSecurityProperties properties = properties(Algorithm.RS256);
        properties.getKey().setPrivateKeyLocation(new FileSystemResource(dir.resolve("absent.pem")));
        assertThrows(IllegalArgumentException.class, () -> facade(properties));
    }

    @Test
    void noKeyConfiguredKeepsEphemeralBehaviour() {
        JwtSecurityProperties properties = properties(Algorithm.RS256);
        String token = facade(properties).generate("u", Map.of());
        // 不同实例的临时密钥互不认可
        assertThrows(IllegalArgumentException.class, () -> facade(properties).parse(token));
    }

    @Test
    void bindsFromApplicationProperties() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JwtFacadeConfiguration.class))
                .withPropertyValues(
                        "triones.security.jwt.algorithm=RS256",
                        "triones.security.jwt.key.private-key-location=file:" + rsaPrivatePem.toAbsolutePath(),
                        "triones.security.jwt.key.key-id=bound-kid")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    JwtFacade jwtFacade = context.getBean(JwtFacade.class);
                    String token = jwtFacade.generate("u", Map.of());
                    assertEquals("bound-kid", kid(token));
                    // 与直接加载同一 PEM 的实例互通
                    JwtSecurityProperties properties = properties(Algorithm.RS256);
                    properties.getKey().setPrivateKeyLocation(new FileSystemResource(rsaPrivatePem));
                    properties.getKey().setKeyId("bound-kid");
                    assertEquals("u", facade(properties).parse(token).getSubject());
                });
    }
}
