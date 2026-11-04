package com.trionesdev.spring.boot.exception.autoconfigure;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@RequiredArgsConstructor
@Configuration
@EnableConfigurationProperties(
        value = {
                ExceptionProperties.class
        }
)
public class ExceptionAutoConfiguration {

}
