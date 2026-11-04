package com.trionesdev.spring.boot.web.autoconfigure.version;

import org.springframework.util.Assert;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class ApiVersionConfigurations {
    private static final Map<ApiVersionType, String> MAPPINGS;

    static {
        Map<ApiVersionType, String> mappings = new EnumMap<>(ApiVersionType.class);
        mappings.put(ApiVersionType.PATH, ApiVersionPathConfiguration.class.getName());
        mappings.put(ApiVersionType.HEADER, ApiVersionHeaderConfiguration.class.getName());
        MAPPINGS = Collections.unmodifiableMap(mappings);
    }

    static String getConfigurationClass(ApiVersionType apiVersionType) {
        String configurationClassName = MAPPINGS.get(apiVersionType);
        Assert.state(configurationClassName != null, () -> "Unknown apiVersion type " + apiVersionType);
        return configurationClassName;
    }

    static ApiVersionType getType(String configurationClassName) {
        for (Map.Entry<ApiVersionType, String> entry : MAPPINGS.entrySet()) {
            if (entry.getValue().equals(configurationClassName)) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("Unknown configuration class " + configurationClassName);
    }
}
