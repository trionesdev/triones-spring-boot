package com.trionesdev.spring.boot.web.autoconfigure.version;

import com.trionesdev.spring.web.apiversion.mvc.PathApiVersionRequestMappingHandlerMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@RequiredArgsConstructor
@Configuration
@Conditional({ApiVersionCondition.class})
public class ApiVersionPathConfiguration {
    private final ApiVersionProperties apiVersionProperties;

    @Bean
    public WebMvcRegistrations pathVersionWebMvcRegistrations() {
        return new WebMvcRegistrations() {
            @Override
            public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                return new PathApiVersionRequestMappingHandlerMapping(apiVersionProperties.getPath().getVersionName());
            }
        };
    }
}
