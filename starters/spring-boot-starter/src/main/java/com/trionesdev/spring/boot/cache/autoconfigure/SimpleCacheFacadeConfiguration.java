package com.trionesdev.spring.boot.cache.autoconfigure;

import com.trionesdev.spring.cache.CacheFacade;
import com.trionesdev.spring.cache.SimpleCacheFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@RequiredArgsConstructor
@Configuration
@ConditionalOnMissingBean({CacheFacade.class})
@Conditional({CacheFacadeCondition.class})
public class SimpleCacheFacadeConfiguration {

    @Bean
    public <K,V> CacheFacade<K,V> simpleCacheFacade(
            ObjectProvider<CacheManager> cacheManager
    ) {
        return new SimpleCacheFacade<>(cacheManager.getIfAvailable());
    }
}
