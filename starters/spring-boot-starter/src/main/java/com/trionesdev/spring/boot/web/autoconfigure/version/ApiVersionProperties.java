package com.trionesdev.spring.boot.web.autoconfigure.version;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "triones.web.api-version")
public class ApiVersionProperties {
    private ApiVersionType type;
    private Path path = new Path();
    private Header header = new Header();


    @Data
    public static class Path {
        private String versionName = "version";
    }

    @Data
    public static class Header {
        private String versionName = "X-Version";
    }

}
