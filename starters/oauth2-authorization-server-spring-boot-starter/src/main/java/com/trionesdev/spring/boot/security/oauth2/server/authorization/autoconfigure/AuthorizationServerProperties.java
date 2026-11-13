package com.trionesdev.spring.boot.security.oauth2.server.authorization.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    /**
     * 签发者（令牌 iss），需要是客户端能访问到的授权服务器外部地址。为空时按请求地址推断。
     */
    private String issuer;
    private Jwk jwk = new Jwk();
    /**
     * 平台自身登录（账号密码、短信、切换租户等）签发令牌时使用的客户端，启动时自动注册 / 更新
     */
    private PlatformClient platformClient = new PlatformClient();
    /**
     * 其它 OAuth2 客户端（第三方应用、下游服务等），启动时按 client-id 注册 / 更新（以配置为准）。
     * 从配置中删除的客户端不会自动从数据库删除。
     */
    private List<Client> clients = new ArrayList<>();

    @Data
    public static class Client {
        private String clientId;
        /**
         * 为空时取 client-id
         */
        private String clientName;
        /**
         * 客户端密钥。可写明文（启动时以 bcrypt 加密后入库），或带编码前缀的密文，如 {bcrypt}$2a$10$...。
         * 为空表示公共客户端（浏览器、移动端）：认证方式为 none，只能走 authorization_code + PKCE。
         * 不要把明文写进仓库，用环境变量注入，如 ${OAUTH2_CLIENT_XXX_SECRET}。
         */
        private String clientSecret;
        /**
         * 认证方式：client_secret_basic、client_secret_post 等；为空时有密钥默认 client_secret_basic + client_secret_post，无密钥为 none
         */
        private Set<String> clientAuthenticationMethods = new LinkedHashSet<>();
        /**
         * 授权类型：client_credentials、authorization_code、refresh_token 等
         */
        private Set<String> authorizationGrantTypes = new LinkedHashSet<>(Set.of("client_credentials"));
        private Set<String> scopes = new LinkedHashSet<>();
        /**
         * authorization_code 回调地址
         */
        private Set<String> redirectUris = new LinkedHashSet<>();
        private Set<String> postLogoutRedirectUris = new LinkedHashSet<>();
        /**
         * authorization_code 是否强制 PKCE，公共客户端始终强制
         */
        private boolean requireProofKey = false;
        /**
         * authorization_code 是否显示授权同意页
         */
        private boolean requireAuthorizationConsent = false;
        private Duration accessTokenTtl = Duration.ofHours(2);
        private Duration refreshTokenTtl = Duration.ofDays(30);
        private boolean reuseRefreshTokens = false;
    }

    @Data
    public static class PlatformClient {
        private String clientId = "belt-platform";
        private String clientName = "Belt Platform";
        /**
         * access_token 有效期，默认与原 triones.security.jwt.expiration 一致（30 天）。
         * 下游通过 JWKS 本地验签时感知不到撤销，建议缩短。
         */
        private Duration accessTokenTtl = Duration.ofDays(30);
        /**
         * refresh_token 有效期，需长于 access_token
         */
        private Duration refreshTokenTtl = Duration.ofDays(90);
        /**
         * 刷新时是否沿用原 refresh_token。默认 false：每次刷新返回新的 refresh_token，旧的立即失效
         */
        private boolean reuseRefreshTokens = false;
    }

    @Data
    public static class Jwk {
        /**
         * 令牌签名密钥。未配置时启动时随机生成（仅限开发环境：重启后之前签发的令牌全部失效，多实例之间也不一致）
         */
        private KeyStore keyStore = new KeyStore();
    }

    @Data
    public static class KeyStore {
        private Resource location;
        private String type = "PKCS12";
        private String password;
        private String alias;
        /**
         * 私钥密码，为空时使用 keystore 密码
         */
        private String keyPassword;
    }
}
