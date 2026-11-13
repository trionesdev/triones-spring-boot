package com.trionesdev.spring.boot.security.oauth2.server.authorization.autoconfigure;

import com.trionesdev.spring.security.KeyStoreConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OAuth2 授权服务器配置
 * <pre>
 * oauth2:
 *   authorization-server:
 *     issuer: https://auth.example.com
 *     jwk:
 *       key-store:
 *         location: file:/etc/belt/oauth2-jwk.p12
 *         password: changeit
 *         alias: oauth2
 *         key-password: changeit
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "triones.security.oauth2.authorization-server")
public class AuthorizationServerProperties {
    private Jwk jwk = new Jwk();


    @Data
    public static class Jwk {
        /**
         * 令牌签名密钥。未配置时启动时随机生成（仅限开发环境：重启后之前签发的令牌全部失效，多实例之间也不一致）
         */
        private KeyStoreConfig keyStore = new KeyStoreConfig();
    }

}
