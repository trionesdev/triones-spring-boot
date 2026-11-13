package com.trionesdev.spring.boot.security.oauth2.autoconfigure;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties("triones.security.oauth2")
public class Oauth2Properties {
    private boolean enabled;
    private AuthorizationServer authorizationServer;

    @Data
    public static class AuthorizationServer {
        private boolean enabled;
        /**
         * 授权服务器模式
         */
        private AuthorizationServerMode mode;
    }

    public enum AuthorizationServerMode{
        /**
         * 独立模式
         */
        STANDALONE,
        /**
         * 嵌入模式
         */
        EMBEDDED
    }
}
