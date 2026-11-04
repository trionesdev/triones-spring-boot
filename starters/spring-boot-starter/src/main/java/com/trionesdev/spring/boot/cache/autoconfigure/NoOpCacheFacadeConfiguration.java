package com.trionesdev.spring.boot.cache.autoconfigure;

import com.trionesdev.spring.cache.CacheFacade;
import com.trionesdev.spring.cache.NoOpCacheFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@RequiredArgsConstructor
@Configuration
@ConditionalOnMissingBean({CacheFacade.class})
@Conditional({CacheFacadeCondition.class})
public class NoOpCacheFacadeConfiguration {

    @Bean
    public <K,V> CacheFacade<K,V> noOpCacheFacade() {
        return new NoOpCacheFacade<>(null);
    }
}
