package com.trionesdev.spring.boot.lock.autoconfigure;

import com.trionesdev.commons.lock.TrionesLockTemplate;
import com.trionesdev.spring.lock.LockAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;


@AutoConfiguration
@EnableConfigurationProperties(value = {
        LockProperties.class
})
@Import({LockAutoConfiguration.LockConfigurationImportSelector.class})
public class LockAutoConfiguration {


    @Bean
    public LockAspect lockAspect(TrionesLockTemplate lockTemplate) {
        return new LockAspect(lockTemplate);
    }

    static class LockConfigurationImportSelector implements ImportSelector {

        @Override
        public String[] selectImports(AnnotationMetadata importingClassMetadata) {
            LockType[] types = LockType.values();
            String[] imports = new String[types.length];
            for (int i = 0; i < types.length; i++) {
                imports[i] = LockConfigurations.getConfigurationClass(types[i]);
            }
            return imports;
        }

    }

}
