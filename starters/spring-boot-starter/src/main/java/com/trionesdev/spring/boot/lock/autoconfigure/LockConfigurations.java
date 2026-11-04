package com.trionesdev.spring.boot.lock.autoconfigure;

import org.springframework.util.Assert;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class LockConfigurations {
    private static final Map<LockType, String> MAPPINGS;

    static {
        Map<LockType, String> mappings = new EnumMap<>(LockType.class);
        mappings.put(LockType.THREAD, ThreadLockConfiguration.class.getName());
        mappings.put(LockType.REDIS, RedisLockConfiguration.class.getName());
        MAPPINGS = Collections.unmodifiableMap(mappings);
    }

    static String getConfigurationClass(LockType lockType) {
        String configurationClassName = MAPPINGS.get(lockType);
        Assert.state(configurationClassName != null, () -> "Unknown lock type " + lockType);
        return configurationClassName;
    }

    static LockType getType(String configurationClassName) {
        for (Map.Entry<LockType, String> entry : MAPPINGS.entrySet()) {
            if (entry.getValue().equals(configurationClassName)) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("Unknown configuration class " + configurationClassName);
    }


}
