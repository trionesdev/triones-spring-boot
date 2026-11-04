package com.trionesdev.spring.boot.opentelemetry.autoconfigure;

import com.trionesdev.spring.boot.opentelemetry.util.OpenTelemetryUtils;
import com.trionesdev.commons.context.actor.ActorContext;
import com.trionesdev.opentelemetry.spring.telemetry.configuration.TelemetryAutoConfiguration;
import com.trionesdev.opentelemetry.spring.web.SkipPatternAutoConfiguration;
import com.trionesdev.opentelemetry.spring.web.WebTelemetryProperties;
import com.trionesdev.opentelemetry.tag.Tags;
import com.trionesdev.opentelemetry.web.servlet.filter.ServletFilterSpanDecorator;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(OpenTelemetry.class)
@AutoConfigureAfter({TelemetryAutoConfiguration.class, SkipPatternAutoConfiguration.class})
@EnableConfigurationProperties(WebTelemetryProperties.class)
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnProperty(name = "opentelemetry.spring.web.enabled", havingValue = "true", matchIfMissing = true)
public class OpentelemetrySpringWebAutoConfiguration {
    private final ActorContext actorContext;

    @Bean
    public ServletFilterSpanDecorator servletFilterSpanDecorator() {
        return new ServletFilterSpanDecorator() {
            @Override
            public void onRequest(HttpServletRequest httpServletRequest, Span span) {
                ServletFilterSpanDecorator.STANDARD_TAGS.onRequest(httpServletRequest, span);
                Optional.ofNullable(OpenTelemetryUtils.actor()).ifPresent(actorContext::setActor);
            }

            @Override
            public void onResponse(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Span span) {
                ServletFilterSpanDecorator.STANDARD_TAGS.onResponse(httpServletRequest, httpServletResponse, span);
                actorContext.resetActor();
            }

            @Override
            public void onError(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Throwable exception, Span span) {
                span.setAttribute(Tags.ERROR, Boolean.TRUE);
                span.recordException(exception);
                if (httpServletResponse.getStatus() == HttpServletResponse.SC_OK) {
                    span.setAttribute(Tags.HTTP_STATUS, 500);
                }
            }

            @Override
            public void onTimeout(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, long timeout, Span span) {
                Map<String, Object> timeoutLogs = new HashMap<>(2);
                timeoutLogs.put("event", "timeout");
                timeoutLogs.put("timeout", timeout);

            }
        };
    }
}
