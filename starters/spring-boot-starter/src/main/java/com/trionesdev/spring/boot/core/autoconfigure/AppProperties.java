package com.trionesdev.spring.boot.core.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "triones.app")
public class AppProperties {
    /**
     * 是否是多租户
     */
    private Boolean multiTenant = false;
    /**
     * 是否是自部署(不对外开放)
     */
    private Boolean selfHosted = true;
}
