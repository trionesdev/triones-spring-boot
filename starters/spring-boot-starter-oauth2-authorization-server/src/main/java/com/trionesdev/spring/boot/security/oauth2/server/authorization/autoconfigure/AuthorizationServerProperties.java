package com.trionesdev.spring.boot.security.oauth2.server.authorization.autoconfigure;

import com.trionesdev.spring.security.KeyStoreConfig;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * OAuth2 授权服务器配置（仅 {@code triones.security.authorization.mode=oauth2} 时生效）
 * <pre>
 * triones:
 *   security:
 *     authorization:
 *       mode: oauth2
 *     oauth2:
 *       authorization-server:
 *         jwk:
 *           key-store:
 *             location: file:/etc/belt/oauth2-jwk.p12
 *             password: changeit
 *             alias: oauth2
 *             key-password: changeit
 *         default-client:              # 平台登录客户端，启动时写入 oauth2_registered_client（已存在不覆盖）
 *           client-id: platform
 *           client-secret: ${OAUTH2_PLATFORM_SECRET}
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "triones.security.oauth2.authorization-server")
public class AuthorizationServerProperties {
    private Jwk jwk = new Jwk();
    private Client defaultClient = new Client();
    private Login login = new Login();


    @Data
    public static class Login {
        /**
         * 浏览器未登录访问授权端点（/oauth2/authorize 等）时的处理方式：
         * json（默认）返回 401 JSON，redirect 跳转登录页。
         * 非浏览器请求（Accept 不含 text/html，如 /oauth2/token）始终返回 401 JSON
         */
        private Unauthenticated unauthenticated = Unauthenticated.JSON;
        /**
         * 登录页路径，以 / 开头（相对应用 context path）。
         * 默认 /login 使用 Spring Security 内置登录页；其他路径需应用自行提供页面，登录表单 POST 到同一路径
         */
        private String page = "/login";
    }

    public enum Unauthenticated {
        /**
         * 跳转登录页，登录成功后回到原授权请求
         */
        REDIRECT,
        /**
         * 返回 401 JSON
         */
        JSON
    }

    @Data
    public static class Jwk {
        /**
         * 令牌签名密钥。未配置时启动时随机生成（仅限开发环境：重启后之前签发的令牌全部失效，多实例之间也不一致）
         */
        private KeyStoreConfig keyStore = new KeyStoreConfig();
    }


    @Data
    public static class Client {
        private String clientId;
        /**
         * 为空时取 client-id
         */
        private String clientName;
        /**
         * 客户端密钥。可写明文（启动时用容器的 PasswordEncoder 编码后入库，未注册时为 {bcrypt}），或带编码前缀的密文，如 {bcrypt}$2a$10$...。
         * 为空表示公共客户端（浏览器、移动端）：认证方式为 none，只能走 authorization_code + PKCE。
         * 不要把明文写进仓库，用环境变量注入，如 ${OAUTH2_CLIENT_XXX_SECRET}。
         */
        private String clientSecret;
        /**
         * 认证方式：client_secret_basic、client_secret_post 等；为空时有密钥默认 client_secret_basic + client_secret_post，无密钥为 none
         */
        private Set<String> clientAuthenticationMethods = new LinkedHashSet<>();
        /**
         * 授权类型：client_credentials、authorization_code、refresh_token 等。
         * 嵌入式令牌签发（平台登录）需要 refresh_token，默认包含
         */
        private Set<String> authorizationGrantTypes = new LinkedHashSet<>(List.of("client_credentials", "refresh_token"));
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

}
