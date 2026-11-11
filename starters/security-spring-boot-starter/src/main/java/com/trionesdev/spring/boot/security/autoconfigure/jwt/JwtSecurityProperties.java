package com.trionesdev.spring.boot.security.autoconfigure.jwt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.core.io.Resource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ConfigurationProperties("triones.security.jwt")
public class JwtSecurityProperties {
    private Algorithm algorithm = Algorithm.RS256; // 签名算法
    private String secret;
    private int expiration = 30 * 60;
    /**
     * 持久化签名密钥（RSA/EC）。未配置时 RSA/EC 每次启动随机生成临时密钥。
     */
    @NestedConfigurationProperty
    @Builder.Default
    private SigningKey key = new SigningKey();
    /**
     * 对外公开签名公钥的 JWKS 端点（RSA/EC），供其他服务通过 jwk-set-uri 远程校验 token。
     */
    @NestedConfigurationProperty
    @Builder.Default
    private Jwks jwks = new Jwks();

    @Data
    @NoArgsConstructor
    public static class Jwks {
        /**
         * 是否开启 JWKS 端点，默认关闭
         */
        private boolean enabled = false;
        /**
         * 端点路径（Spring Authorization Server 默认为 /oauth2/jwks）
         */
        private String path = "/auth/jwks";
        /**
         * 响应 Cache-Control max-age；设为 0 表示不缓存
         */
        private Duration cacheMaxAge = Duration.ofMinutes(5);
    }

    /**
     * 密钥来源，三选一：{@code jwk-location}、{@code private-key-location}（或仅用于校验时的
     * {@code public-key-location}）、{@code key-store.location}。
     */
    @Data
    @NoArgsConstructor
    public static class KeySource {
        /**
         * 密钥 ID（JWT 头中的 kid）。未设置时使用 JWK thumbprint，多实例加载同一密钥时保持一致。
         */
        private String keyId;
        /**
         * JWK JSON 文件，支持 RSA / EC / oct。签名密钥必须包含私钥，验证密钥可只含公钥。
         */
        private Resource jwkLocation;
        /**
         * PKCS#8 PEM 私钥（-----BEGIN PRIVATE KEY-----）。
         */
        private Resource privateKeyLocation;
        /**
         * X.509 PEM 公钥（-----BEGIN PUBLIC KEY-----）。签名密钥：RSA 可选（可由私钥推导），EC 必填；
         * 验证密钥：可单独配置。
         */
        private Resource publicKeyLocation;
        @NestedConfigurationProperty
        private KeyStoreProperties keyStore = new KeyStoreProperties();
    }

    /**
     * 当前签名密钥 + 仅用于校验的旧密钥（密钥轮换）。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    @ToString(callSuper = true)
    @NoArgsConstructor
    public static class SigningKey extends KeySource {
        /**
         * 仅用于校验的额外密钥，例如轮换前的旧密钥。旧 token 全部过期（≥ expiration）后可移除。
         */
        private List<KeySource> verificationKeys = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class KeyStoreProperties {
        /**
         * keystore 文件，例如 classpath:jwt.p12、file:/etc/app/jwt.jks
         */
        private Resource location;
        /**
         * PKCS12（默认）或 JKS
         */
        private String type = "PKCS12";
        private String password;
        /**
         * 密钥别名；keystore 中只有一个密钥（或证书）条目时可省略
         */
        private String alias;
        /**
         * 私钥密码，未设置时使用 keystore 密码
         */
        private String keyPassword;
    }
}
