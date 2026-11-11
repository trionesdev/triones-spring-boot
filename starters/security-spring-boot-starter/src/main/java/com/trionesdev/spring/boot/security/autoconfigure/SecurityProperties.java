package com.trionesdev.spring.boot.security.autoconfigure;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties("triones.security")
public class SecurityProperties {
    private String[] defaultMatchers = {"/favicon.ico"};
    private String[] excludeMatchers = {};
    private String[] excludeGetMatchers = {};
    private String[] excludePostMatchers = {};
    private String[] excludePutMatchers = {};
    private String[] excludeDeleteMatchers = {};
    private String[] ignoreMatchers = {};
    private ResourceServer resourceServer = new ResourceServer();

    @Data
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ResourceServer {
        private Boolean enabled = false;
        private Jwt jwt = new Jwt();

        @Data
        public static class Jwt {
            private String issuerUri;
            private String jwkSetUri;
        }
    }
}
