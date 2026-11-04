package com.trionesdev.spring.boot.cache.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

@AutoConfiguration( after = {
        CacheAutoConfiguration.class,
//        GenericCacheFacadeConfiguration.class,
//        HazelcastCacheFacadeConfiguration.class,
//        InfinispanCacheFacadeConfiguration.class,
//        JCacheCacheFacadeConfiguration.class,
//        CouchbaseCacheFacadeConfiguration.class,
//        RedisCacheFacadeConfiguration.class,
//        CaffeineCacheFacadeConfiguration.class,
//        Cache2kCacheFacadeConfiguration.class,
//        SimpleCacheFacadeConfiguration.class,
//        NoOpCacheFacadeConfiguration.class
})
@Import({CacheFacadeAutoConfiguration.CacheFacadeConfigurationImportSelector.class})
public class CacheFacadeAutoConfiguration {

    static class CacheFacadeConfigurationImportSelector implements ImportSelector {

        @Override
        public String[] selectImports(AnnotationMetadata importingClassMetadata) {
            CacheType[] types = CacheType.values();
            String[] imports = new String[types.length];
            for (int i = 0; i < types.length; i++) {
                imports[i] = CacheFacadeConfigurations.getConfigurationClass(types[i]);
            }
            return imports;
        }

    }

}
