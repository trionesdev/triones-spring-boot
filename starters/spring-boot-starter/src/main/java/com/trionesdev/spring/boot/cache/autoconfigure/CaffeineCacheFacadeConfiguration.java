package com.trionesdev.spring.boot.cache.autoconfigure;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.CaffeineSpec;
import com.trionesdev.spring.cache.CacheFacade;
import com.trionesdev.spring.cache.CaffeineCacheFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
@Configuration
@ConditionalOnClass({Caffeine.class})
@ConditionalOnMissingBean({CacheFacade.class})
@Conditional({CacheFacadeCondition.class})
public class CaffeineCacheFacadeConfiguration {

    @Bean
    public <K, V> CaffeineCacheFacade<K, V> cacheFacade(
            CacheProperties cacheProperties,
            ObjectProvider<Caffeine<Object, Object>> caffeine,
            ObjectProvider<CaffeineSpec> caffeineSpec,
            ObjectProvider<CacheManager> cacheManager
    ) {
        CaffeineCacheFacade<K, V> caffeineCacheFacade = new CaffeineCacheFacade<>(cacheManager.getIfAvailable());
        String specification = cacheProperties.getCaffeine().getSpec();
        if (StringUtils.hasText(specification)) {
            caffeineCacheFacade.setCacheSpecification(specification);
        } else if (caffeineSpec.getIfAvailable() != null) {
            caffeineCacheFacade.setCaffeineSpec(caffeineSpec.getIfAvailable());
        } else if (caffeine.getIfAvailable() != null) {
            caffeineCacheFacade.setCaffeine(caffeine.getIfAvailable());
        }
        return caffeineCacheFacade;
    }

}
