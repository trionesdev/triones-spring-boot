package com.trionesdev.spring.boot.cache.autoconfigure;

import com.trionesdev.spring.cache.CacheFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@RequiredArgsConstructor
@Configuration
@ConditionalOnMissingBean({CacheFacade.class})
@Conditional({CacheFacadeCondition.class})
public class CouchbaseCacheFacadeConfiguration {
}
