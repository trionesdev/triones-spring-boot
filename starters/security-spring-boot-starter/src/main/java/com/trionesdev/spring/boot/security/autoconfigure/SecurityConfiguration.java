package com.trionesdev.spring.boot.security.autoconfigure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(value = {
        SecurityProperties.class
})
public class SecurityConfiguration {
}
