package com.trionesdev.spring.boot.lock.autoconfigure;

import com.trionesdev.commons.lock.TrionesLockTemplate;
import com.trionesdev.commons.lock.thread.ThreadLockTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnMissingBean({TrionesLockTemplate.class})
@Conditional({LockCondition.class})
public class ThreadLockConfiguration {
    @Bean
    public ThreadLockTemplate threadLockTemplate() {
        return new ThreadLockTemplate();
    }
}
