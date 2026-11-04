package com.trionesdev.spring.boot.cache.autoconfigure;


import org.springframework.boot.autoconfigure.cache.CacheType;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class CacheFacadeConfigurations {
    private static final Map<CacheType, String> MAPPINGS;

    static {
        Map<CacheType, String> mappings = new EnumMap<>(CacheType.class);
        mappings.put(CacheType.GENERIC, GenericCacheFacadeConfiguration.class.getName());
        mappings.put(CacheType.HAZELCAST, HazelcastCacheFacadeConfiguration.class.getName());
        mappings.put(CacheType.INFINISPAN, InfinispanCacheFacadeConfiguration.class.getName());
        mappings.put(CacheType.JCACHE, JCacheCacheFacadeConfiguration.class.getName());
        mappings.put(CacheType.COUCHBASE, CouchbaseCacheFacadeConfiguration.class.getName());
        mappings.put(CacheType.REDIS, RedisCacheFacadeConfiguration.class.getName());
        mappings.put(CacheType.CAFFEINE, CaffeineCacheFacadeConfiguration.class.getName());
        mappings.put(CacheType.CACHE2K, Cache2kCacheFacadeConfiguration.class.getName());
        mappings.put(CacheType.SIMPLE, SimpleCacheFacadeConfiguration.class.getName());
        mappings.put(CacheType.NONE, NoOpCacheFacadeConfiguration.class.getName());
        MAPPINGS = Collections.unmodifiableMap(mappings);
    }

    private CacheFacadeConfigurations() {
    }

    static String getConfigurationClass(CacheType cacheType) {
        String configurationClassName = MAPPINGS.get(cacheType);
        Assert.state(configurationClassName != null, () -> "Unknown cache type " + cacheType);
        return configurationClassName;
    }

    static CacheType getType(String configurationClassName) {
        for (Map.Entry<CacheType, String> entry : MAPPINGS.entrySet()) {
            if (entry.getValue().equals(configurationClassName)) {
                return entry.getKey();
            }
        }
        throw new IllegalStateException("Unknown configuration class " + configurationClassName);
    }
}
