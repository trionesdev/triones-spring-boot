package com.trionesdev.spring.boot.feign.autoconfigure;

import com.trionesdev.commons.feign.codec.DefaultErrorDecoder;
import feign.Logger;
import feign.codec.ErrorDecoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignAutoConfiguration {
    @Bean
    public ErrorDecoder errorDecoder() {
        return new DefaultErrorDecoder();
    }

    @Bean
    @ConditionalOnProperty(prefix = "triones.feign.logger", value = "enable", havingValue = "true")
    Logger.Level feignLevel() {
        return Logger.Level.FULL;
    }

}
