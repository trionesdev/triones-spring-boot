package com.trionesdev.spring.boot.web.autoconfigure.version;

import com.trionesdev.spring.boot.web.autoconfigure.version.ApiVersionAutoConfiguration.ApiVersionConfigurationImportSelector;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;


@RequiredArgsConstructor
@AutoConfiguration(after = {
        ApiVersionPathConfiguration.class, ApiVersionHeaderConfiguration.class
})
@EnableConfigurationProperties(ApiVersionProperties.class)
@ConditionalOnWebApplication
@Import({ApiVersionConfigurationImportSelector.class})
public class ApiVersionAutoConfiguration {

    static class ApiVersionConfigurationImportSelector implements ImportSelector {

        @Override
        public String[] selectImports(AnnotationMetadata importingClassMetadata) {
            ApiVersionType[] types = ApiVersionType.values();
            String[] imports = new String[types.length];
            for (int i = 0; i < types.length; i++) {
                imports[i] = ApiVersionConfigurations.getConfigurationClass(types[i]);
            }
            return imports;
        }

    }
}
