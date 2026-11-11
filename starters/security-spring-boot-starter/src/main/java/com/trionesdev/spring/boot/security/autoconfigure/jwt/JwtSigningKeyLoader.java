package com.trionesdev.spring.boot.security.autoconfigure.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * 从配置加载持久化的 JWT 签名密钥。
 */
public final class JwtSigningKeyLoader {
    private static final String PREFIX = "triones.security.jwt.key.";

    private JwtSigningKeyLoader() {
    }

    /**
     * @return 配置的签名密钥；未配置任何密钥来源时返回 null
     */
    public static JWK load(JwtSecurityProperties.SigningKey config, JWSAlgorithm algorithm) {
        if (config == null) {
            return null;
        }
        Resource keyStoreLocation = config.getKeyStore() == null ? null : config.getKeyStore().getLocation();
        int sources = (config.getJwkLocation() != null ? 1 : 0)
                + (config.getPrivateKeyLocation() != null ? 1 : 0)
                + (keyStoreLocation != null ? 1 : 0);
        if (sources == 0) {
            if (config.getPublicKeyLocation() != null) {
                throw new IllegalArgumentException(PREFIX + "public-key-location requires private-key-location");
            }
            return null;
        }
        if (sources > 1) {
            throw new IllegalArgumentException("Configure only one of " + PREFIX + "jwk-location, "
                    + PREFIX + "private-key-location, " + PREFIX + "key-store.location");
        }
        if (JWSAlgorithm.Family.HMAC_SHA.contains(algorithm) && config.getJwkLocation() == null) {
            throw new IllegalArgumentException(algorithm + " uses triones.security.jwt.secret or an oct JWK ("
                    + PREFIX + "jwk-location), not a PEM/keystore private key");
        }
        try {
            JWK jwk;
            if (config.getJwkLocation() != null) {
                jwk = loadJwk(config.getJwkLocation());
            } else if (config.getPrivateKeyLocation() != null) {
                jwk = loadPem(config.getPrivateKeyLocation(), config.getPublicKeyLocation());
            } else {
                jwk = loadKeyStore(config.getKeyStore());
            }
            return withKeyId(jwk, config.getKeyId());
        } catch (IOException | GeneralSecurityException | ParseException | JOSEException ex) {
            throw new IllegalArgumentException("Failed to load JWT signing key: " + ex.getMessage(), ex);
        }
    }

    private static JWK loadJwk(Resource resource) throws IOException, ParseException {
        JWK jwk = JWK.parse(read(resource));
        if (!jwk.isPrivate()) {
            throw new IllegalArgumentException(PREFIX + "jwk-location must contain a private key: " + resource);
        }
        return jwk;
    }

    private static JWK loadPem(Resource privateLocation, Resource publicLocation)
            throws IOException, GeneralSecurityException {
        String pem = read(privateLocation);
        if (pem.contains("BEGIN RSA PRIVATE KEY") || pem.contains("BEGIN EC PRIVATE KEY")) {
            throw new IllegalArgumentException(PREFIX + "private-key-location must be PKCS#8 (BEGIN PRIVATE KEY); convert with: "
                    + "openssl pkcs8 -topk8 -nocrypt -in key.pem -out key-pkcs8.pem");
        }
        if (pem.contains("BEGIN ENCRYPTED PRIVATE KEY")) {
            throw new IllegalArgumentException("Encrypted PEM is not supported; use an unencrypted PKCS#8 PEM or a keystore");
        }
        byte[] der = decodePem(pem, "PRIVATE KEY", privateLocation);
        PrivateKey privateKey = parsePrivateKey(der);
        PublicKey publicKey = publicLocation == null ? null : parsePublicKey(
                decodePem(read(publicLocation), "PUBLIC KEY", publicLocation), privateKey.getAlgorithm());
        if (privateKey instanceof RSAPrivateKey rsaPrivate) {
            RSAPublicKey rsaPublic = publicKey != null ? (RSAPublicKey) publicKey : deriveRsaPublicKey(rsaPrivate);
            return new RSAKey.Builder(rsaPublic).privateKey(rsaPrivate).build();
        }
        if (privateKey instanceof ECPrivateKey ecPrivate) {
            if (publicKey == null) {
                throw new IllegalArgumentException(PREFIX + "public-key-location is required for EC private keys");
            }
            ECPublicKey ecPublic = (ECPublicKey) publicKey;
            return new ECKey.Builder(Curve.forECParameterSpec(ecPublic.getParams()), ecPublic).privateKey(ecPrivate).build();
        }
        throw new IllegalArgumentException("Unsupported private key algorithm: " + privateKey.getAlgorithm());
    }

    private static JWK loadKeyStore(JwtSecurityProperties.KeyStoreProperties config)
            throws IOException, GeneralSecurityException {
        char[] storePassword = config.getPassword() == null ? null : config.getPassword().toCharArray();
        KeyStore keyStore = KeyStore.getInstance(config.getType() == null ? "PKCS12" : config.getType());
        try (InputStream in = config.getLocation().getInputStream()) {
            keyStore.load(in, storePassword);
        }
        String alias = config.getAlias() != null ? config.getAlias() : singleKeyAlias(keyStore);
        char[] keyPassword = config.getKeyPassword() != null ? config.getKeyPassword().toCharArray() : storePassword;
        Key key = keyStore.getKey(alias, keyPassword);
        if (!(key instanceof PrivateKey privateKey)) {
            throw new IllegalArgumentException("No private key found for alias '" + alias + "' in " + config.getLocation());
        }
        Certificate certificate = keyStore.getCertificate(alias);
        if (certificate == null) {
            throw new IllegalArgumentException("No certificate found for alias '" + alias + "' in " + config.getLocation());
        }
        PublicKey publicKey = certificate.getPublicKey();
        if (privateKey instanceof RSAPrivateKey rsaPrivate && publicKey instanceof RSAPublicKey rsaPublic) {
            return new RSAKey.Builder(rsaPublic).privateKey(rsaPrivate).build();
        }
        if (privateKey instanceof ECPrivateKey ecPrivate && publicKey instanceof ECPublicKey ecPublic) {
            return new ECKey.Builder(Curve.forECParameterSpec(ecPublic.getParams()), ecPublic).privateKey(ecPrivate).build();
        }
        throw new IllegalArgumentException("Unsupported key algorithm in keystore: " + privateKey.getAlgorithm());
    }

    private static String singleKeyAlias(KeyStore keyStore) throws GeneralSecurityException {
        List<String> keyAliases = new ArrayList<>();
        for (String alias : Collections.list(keyStore.aliases())) {
            if (keyStore.isKeyEntry(alias)) {
                keyAliases.add(alias);
            }
        }
        if (keyAliases.size() != 1) {
            throw new IllegalArgumentException(PREFIX + "key-store.alias is required, key entries: " + keyAliases);
        }
        return keyAliases.get(0);
    }

    private static JWK withKeyId(JWK jwk, String keyId) throws JOSEException {
        String kid = keyId != null && !keyId.isBlank() ? keyId
                : jwk.getKeyID() != null ? jwk.getKeyID()
                : jwk.computeThumbprint().toString();
        if (kid.equals(jwk.getKeyID())) {
            return jwk;
        }
        if (jwk instanceof RSAKey rsa) {
            return new RSAKey.Builder(rsa).keyID(kid).build();
        }
        if (jwk instanceof ECKey ec) {
            return new ECKey.Builder(ec).keyID(kid).build();
        }
        if (jwk instanceof OctetSequenceKey oct) {
            return new OctetSequenceKey.Builder(oct).keyID(kid).build();
        }
        throw new IllegalArgumentException("Unsupported JWK type: " + jwk.getKeyType());
    }

    private static PrivateKey parsePrivateKey(byte[] der) throws GeneralSecurityException {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (GeneralSecurityException rsaFailure) {
            try {
                return KeyFactory.getInstance("EC").generatePrivate(spec);
            } catch (GeneralSecurityException ecFailure) {
                ecFailure.addSuppressed(rsaFailure);
                throw ecFailure;
            }
        }
    }

    private static PublicKey parsePublicKey(byte[] der, String algorithm) throws GeneralSecurityException {
        return KeyFactory.getInstance(algorithm).generatePublic(new X509EncodedKeySpec(der));
    }

    private static RSAPublicKey deriveRsaPublicKey(RSAPrivateKey privateKey) throws GeneralSecurityException {
        if (!(privateKey instanceof RSAPrivateCrtKey crt)) {
            throw new IllegalArgumentException(PREFIX + "public-key-location is required: RSA private key has no public exponent");
        }
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
    }

    private static byte[] decodePem(String pem, String type, Resource source) {
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";
        int start = pem.indexOf(begin);
        int stop = pem.indexOf(end);
        if (start < 0 || stop < start) {
            throw new IllegalArgumentException("Expected PEM block '" + begin + "' in " + source);
        }
        String base64 = pem.substring(start + begin.length(), stop).replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private static String read(Resource resource) throws IOException {
        if (!resource.exists()) {
            throw new IllegalArgumentException("JWT key resource not found: " + resource);
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
