package com.trionesdev.spring.boot.cache.autoconfigure;

import com.trionesdev.spring.cache.CacheFacade;
import com.trionesdev.spring.cache.RedisCacheFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

@RequiredArgsConstructor
@Configuration
@AutoConfigureAfter(RedisAutoConfiguration.class)
@ConditionalOnClass({RedisTemplate.class})
@ConditionalOnMissingBean({CacheFacade.class})
@Conditional({CacheFacadeCondition.class})
public class RedisCacheFacadeConfiguration {

    @Bean
    public <K, V> RedisCacheFacade<K, V> cacheFacade(
            ObjectProvider<CacheManager> cacheManager,
            ObjectProvider<RedisTemplate<K, V>> redisTemplate
    ) {
        return new RedisCacheFacade<>(cacheManager.getIfAvailable(), redisTemplate.getIfAvailable());
    }

}
