package com.trionesdev.spring.boot.auth.client.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 服务间调用的 client_credentials 客户端配置（由 AuthClientAutoConfiguration 使用），
 * 结构与 Spring Security 的 {@code spring.security.oauth2.client.*} 一致：
 * <pre>
 * triones:
 *   security:
 *     auth:
 *       client:
 *         registration:
 *           order-service:            # registrationId
 *             provider: triones-auth  # 省略时与 registrationId 同名
 *             client-id: order-service
 *             client-secret: secret
 *             authorization-grant-type: client_credentials
 *             client-authentication-method: client_secret_basic
 *             scope: order.read
 *         clock-skew: 60s             # token 到期前多久重新申请，默认 60 秒
 *         provider:
 *           triones-auth:
 *             token-uri: http://auth-server/auth/token
 * </pre>
 */
@Data
@ConfigurationProperties("triones.security.auth.client")
public class AuthClientProperties {
    private Map<String, Registration> registration = new HashMap<>();
    private Map<String, Provider> provider = new HashMap<>();
    /**
     * token 到期前多久视为过期并重新申请，默认 60 秒
     */
    private Duration clockSkew = Duration.ofSeconds(60);

    @Data
    public static class Registration {
        /**
         * provider 名称，省略时使用 registrationId
         */
        private String provider;
        private String clientId;
        private String clientSecret;
        /**
         * 目前仅支持 client_credentials（默认）
         */
        private String authorizationGrantType;
        /**
         * client_secret_basic（默认）或 client_secret_post
         */
        private String clientAuthenticationMethod;
        private Set<String> scope = new LinkedHashSet<>();
    }

    @Data
    public static class Provider {
        /**
         * 授权服务器 token 端点
         */
        private String tokenUri;
    }

}
