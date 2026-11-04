package com.trionesdev.spring.boot.lock.autoconfigure;

import com.trionesdev.commons.lock.TrionesLockTemplate;
import com.trionesdev.commons.lock.redis.RedisLockTemplate;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@RequiredArgsConstructor
@Configuration
@ConditionalOnClass({ RedissonClient.class })
@ConditionalOnMissingBean({TrionesLockTemplate.class})
@Conditional({LockCondition.class})
public class RedisLockConfiguration {

    @Bean
    public RedisLockTemplate redisLockTemplate(RedissonClient redissonClient) {
        return new RedisLockTemplate(redissonClient);
    }
}
