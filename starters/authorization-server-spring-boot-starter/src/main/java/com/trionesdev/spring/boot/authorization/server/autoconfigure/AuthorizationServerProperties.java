package com.trionesdev.spring.boot.authorization.server.autoconfigure;

import com.trionesdev.spring.security.authorization.server.jwks.JwksEndpointConfigurer;
import com.trionesdev.spring.security.authorization.server.token.ClientAuthenticationMethod;
import com.trionesdev.spring.security.authorization.server.token.TokenEndpointConfigurer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 授权服务器配置。签名密钥沿用 {@code triones.security.jwt.*}（{@code JwtFacade}）。
 */
@ConfigurationProperties(AuthorizationServerProperties.PREFIX)
public class AuthorizationServerProperties {
    public static final String PREFIX = "triones.security.authorization-server";

    /**
     * 是否开启授权服务器端点，默认开启
     */
    private boolean enabled = true;
    /**
     * 写入 token 的 iss，资源服务器可据此校验签发方
     */
    private String issuer;
    @NestedConfigurationProperty
    private Token token = new Token();
    @NestedConfigurationProperty
    private Jwks jwks = new Jwks();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Token getToken() {
        return token;
    }

    public void setToken(Token token) {
        this.token = token;
    }

    public Jwks getJwks() {
        return jwks;
    }

    public void setJwks(Jwks jwks) {
        this.jwks = jwks;
    }

    /**
     * token 端点（client_credentials）
     */
    public static class Token {
        /**
         * 是否开启 token 端点，默认开启
         */
        private boolean enabled = true;
        /**
         * 端点路径
         */
        private String path = TokenEndpointConfigurer.DEFAULT_PATH;
        /**
         * 默认 access token 有效期（客户端未单独设置时）
         */
        private Duration accessTokenTimeToLive = Duration.ofMinutes(30);
        /**
         * 登记的客户端；容器中已有 RegisteredClientRepository 时忽略
         */
        private List<Client> clients = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public Duration getAccessTokenTimeToLive() {
            return accessTokenTimeToLive;
        }

        public void setAccessTokenTimeToLive(Duration accessTokenTimeToLive) {
            this.accessTokenTimeToLive = accessTokenTimeToLive;
        }

        public List<Client> getClients() {
            return clients;
        }

        public void setClients(List<Client> clients) {
            this.clients = clients;
        }
    }

    public static class Client {
        private String clientId;
        /**
         * PasswordEncoder 编码后的 secret。未注册 PasswordEncoder Bean 时使用 DelegatingPasswordEncoder，
         * 需带前缀，例如 {bcrypt}$2a$10$...、开发环境 {noop}secret
         */
        private String clientSecret;
        /**
         * 允许申请的 scope
         */
        private Set<String> scopes = new LinkedHashSet<>();
        /**
         * 允许的认证方式，默认 client-secret-basic 与 client-secret-post 均可
         */
        private Set<ClientAuthenticationMethod> clientAuthenticationMethods = new LinkedHashSet<>();
        /**
         * access token 有效期，未设置时使用 token.access-token-time-to-live
         */
        private Duration accessTokenTimeToLive;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public Set<String> getScopes() {
            return scopes;
        }

        public void setScopes(Set<String> scopes) {
            this.scopes = scopes;
        }

        public Set<ClientAuthenticationMethod> getClientAuthenticationMethods() {
            return clientAuthenticationMethods;
        }

        public void setClientAuthenticationMethods(Set<ClientAuthenticationMethod> clientAuthenticationMethods) {
            this.clientAuthenticationMethods = clientAuthenticationMethods;
        }

        public Duration getAccessTokenTimeToLive() {
            return accessTokenTimeToLive;
        }

        public void setAccessTokenTimeToLive(Duration accessTokenTimeToLive) {
            this.accessTokenTimeToLive = accessTokenTimeToLive;
        }
    }

    /**
     * JWKS 端点：公开签名公钥（RSA / EC），HMAC 时必须关闭
     */
    public static class Jwks {
        /**
         * 是否开启 JWKS 端点，默认开启
         */
        private boolean enabled = true;
        /**
         * 端点路径
         */
        private String path = JwksEndpointConfigurer.DEFAULT_PATH;
        /**
         * 响应 Cache-Control max-age；设为 0 表示不缓存
         */
        private Duration cacheMaxAge = JwksEndpointConfigurer.DEFAULT_CACHE_MAX_AGE;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public Duration getCacheMaxAge() {
            return cacheMaxAge;
        }

        public void setCacheMaxAge(Duration cacheMaxAge) {
            this.cacheMaxAge = cacheMaxAge;
        }
    }
}
