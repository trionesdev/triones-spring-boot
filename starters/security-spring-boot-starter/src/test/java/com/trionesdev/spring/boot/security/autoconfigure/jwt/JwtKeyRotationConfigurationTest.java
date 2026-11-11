package com.trionesdev.spring.boot.security.autoconfigure.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.OctetSequenceKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtKeyRotationConfigurationTest {

    @TempDir
    static Path dir;

    static RSAKey oldKey;
    static RSAKey newKey;
    static Path oldPrivatePem;
    static Path oldPublicPem;
    static Path newPrivatePem;

    @BeforeAll
    static void generateKeys() throws Exception {
        oldKey = new RSAKeyGenerator(2048).generate();
        newKey = new RSAKeyGenerator(2048).generate();
        oldPrivatePem = writePem("old-private.pem", "PRIVATE KEY", oldKey.toRSAPrivateKey().getEncoded());
        oldPublicPem = writePem("old-public.pem", "PUBLIC KEY", oldKey.toRSAPublicKey().getEncoded());
        newPrivatePem = writePem("new-private.pem", "PRIVATE KEY", newKey.toRSAPrivateKey().getEncoded());
    }

    private static Path writePem(String name, String type, byte[] der) throws Exception {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        Path path = dir.resolve(name);
        Files.writeString(path, "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n");
        return path;
    }

    private static JwtSecurityProperties.KeySource source(java.util.function.Consumer<JwtSecurityProperties.KeySource> c) {
        JwtSecurityProperties.KeySource source = new JwtSecurityProperties.KeySource();
        c.accept(source);
        return source;
    }

    private static JwtFacade facade(JwtSecurityProperties properties) {
        return new JwtFacadeConfiguration().jwtFacade(properties);
    }

    private static JwtSecurityProperties signingWith(Path privatePem) {
        JwtSecurityProperties properties = new JwtSecurityProperties();
        properties.setAlgorithm(Algorithm.RS256);
        properties.getKey().setPrivateKeyLocation(new FileSystemResource(privatePem));
        return properties;
    }

    @Test
    void rotationWithPublicPemVerificationKey() {
        // 轮换前：旧私钥签发（kid 默认为 thumbprint）
        String oldToken = facade(signingWith(oldPrivatePem)).generate("old", Map.of());

        // 轮换后：新私钥签发，旧公钥校验
        JwtSecurityProperties rotated = signingWith(newPrivatePem);
        rotated.getKey().getVerificationKeys().add(
                source(s -> s.setPublicKeyLocation(new FileSystemResource(oldPublicPem))));
        JwtFacade facade = facade(rotated);
        assertEquals("old", facade.parse(oldToken).getSubject());
        assertEquals("new", facade.parse(facade.generate("new", Map.of())).getSubject());

        // 移除旧密钥后旧 token 失效
        assertThrows(IllegalArgumentException.class, () -> facade(signingWith(newPrivatePem)).parse(oldToken));
    }

    @Test
    void jwkSetExposesBothKeysWithStableKids() throws Exception {
        JwtSecurityProperties rotated = signingWith(newPrivatePem);
        rotated.getKey().getVerificationKeys().add(
                source(s -> s.setPrivateKeyLocation(new FileSystemResource(oldPrivatePem))));
        JWKSet set = JWKSet.parse(facade(rotated).getPublicJwkSet());
        assertEquals(List.of(newKey.computeThumbprint().toString(), oldKey.computeThumbprint().toString()),
                set.getKeys().stream().map(k -> k.getKeyID()).toList());
        assertTrue(set.getKeys().stream().noneMatch(k -> k.isPrivate()));
    }

    @Test
    void publicJwkAndKeystoreCertificateAccepted() throws Exception {
        String oldToken = facade(signingWith(oldPrivatePem)).generate("old", Map.of());

        Path publicJwk = dir.resolve("old-public.jwk.json");
        Files.writeString(publicJwk, oldKey.toPublicJWK().toJSONString());
        JwtSecurityProperties withJwk = signingWith(newPrivatePem);
        withJwk.getKey().getVerificationKeys().add(source(s -> s.setJwkLocation(new FileSystemResource(publicJwk))));
        assertEquals("old", facade(withJwk).parse(oldToken).getSubject());

        // keystore 中仅有证书条目也可作为验证密钥
        Path keyStore = dir.resolve("old.p12");
        Path cert = dir.resolve("old.cer");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        run(keytool, "-genkeypair", "-keyalg", "RSA", "-keysize", "2048", "-alias", "jwt", "-dname", "CN=old",
                "-validity", "1", "-storetype", "PKCS12", "-keystore", keyStore.toString(), "-storepass", "changeit");
        run(keytool, "-exportcert", "-alias", "jwt", "-keystore", keyStore.toString(), "-storepass", "changeit",
                "-file", cert.toString());
        Path trustStore = dir.resolve("trust.p12");
        run(keytool, "-importcert", "-noprompt", "-alias", "old-jwt", "-file", cert.toString(),
                "-storetype", "PKCS12", "-keystore", trustStore.toString(), "-storepass", "changeit");

        JwtSecurityProperties ksSigning = new JwtSecurityProperties();
        ksSigning.setAlgorithm(Algorithm.RS256);
        ksSigning.getKey().getKeyStore().setLocation(new FileSystemResource(keyStore));
        ksSigning.getKey().getKeyStore().setPassword("changeit");
        String ksToken = facade(ksSigning).generate("ks", Map.of());

        JwtSecurityProperties withTrustStore = signingWith(newPrivatePem);
        withTrustStore.getKey().getVerificationKeys().add(source(s -> {
            s.getKeyStore().setLocation(new FileSystemResource(trustStore));
            s.getKeyStore().setPassword("changeit");
        }));
        assertEquals("ks", facade(withTrustStore).parse(ksToken).getSubject());
    }

    private static void run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
    }

    @Test
    void verificationKeysRequirePersistentSigningKey() {
        JwtSecurityProperties properties = new JwtSecurityProperties();
        properties.setAlgorithm(Algorithm.RS256);
        properties.getKey().getVerificationKeys().add(
                source(s -> s.setPublicKeyLocation(new FileSystemResource(oldPublicPem))));
        assertThrows(IllegalArgumentException.class, () -> facade(properties));
    }

    @Test
    void emptyVerificationKeyEntryRejected() {
        JwtSecurityProperties properties = signingWith(newPrivatePem);
        properties.getKey().getVerificationKeys().add(new JwtSecurityProperties.KeySource());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> facade(properties));
        assertTrue(ex.getMessage().contains("verification-keys[0]"));
    }

    @Test
    void hmacSecretRotationWithOctJwk() throws Exception {
        OctetSequenceKey old = new OctetSequenceKeyGenerator(256).keyID("old").generate();
        Path oldJwk = dir.resolve("old-oct.jwk.json");
        Files.writeString(oldJwk, old.toJSONString());
        String oldToken = new JwtFacade(com.trionesdev.commons.core.jwt.JwtConfig.builder()
                .algorithm(com.nimbusds.jose.JWSAlgorithm.HS256).expiration(60).build(), old).generate("u", Map.of());

        JwtSecurityProperties properties = new JwtSecurityProperties();
        properties.setAlgorithm(Algorithm.HS256);
        properties.setSecret("new-secret");
        properties.getKey().getVerificationKeys().add(source(s -> s.setJwkLocation(new FileSystemResource(oldJwk))));
        assertEquals("u", facade(properties).parse(oldToken).getSubject());
    }

    @Test
    void bindsVerificationKeysFromApplicationProperties() {
        String oldToken = facade(signingWith(oldPrivatePem)).generate("old", Map.of());
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JwtFacadeConfiguration.class))
                .withPropertyValues(
                        "triones.security.jwt.algorithm=RS256",
                        "triones.security.jwt.key.private-key-location=file:" + newPrivatePem.toAbsolutePath(),
                        "triones.security.jwt.key.verification-keys[0].public-key-location=file:" + oldPublicPem.toAbsolutePath())
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals("old", context.getBean(JwtFacade.class).parse(oldToken).getSubject());
                });
    }
}
