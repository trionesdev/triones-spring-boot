package com.trionesdev.spring.boot.opentelemetry.autoconfigure;

import com.trionesdev.spring.boot.opentelemetry.util.OpenTelemetryUtils;
import com.trionesdev.commons.context.actor.ActorContext;
import com.trionesdev.opentelemetry.feign.FeignSpanDecorator;
import com.trionesdev.opentelemetry.spring.telemetry.configuration.TelemetryAutoConfiguration;
import feign.Request;
import feign.Response;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@RequiredArgsConstructor
@Configuration
@ConditionalOnBean(OpenTelemetry.class)
@AutoConfigureAfter({TelemetryAutoConfiguration.class})
@ConditionalOnProperty(name = "opentelemetry.spring.cloud.feign.enabled", havingValue = "true", matchIfMissing = true)
public class OpentelemetryFeignAuoConfiguration {

    private final ActorContext actorContext;

    @Bean
    public FeignSpanDecorator feignSpanDecorator(){
        final FeignSpanDecorator.StandardTags standard = new FeignSpanDecorator.StandardTags();
        return new FeignSpanDecorator() {
            @Override
            public void onRequest(Request request, Request.Options options, Span span) {
                standard.onRequest(request, options, span);
                OpenTelemetryUtils.baggage(actorContext.getActor());
            }

            @Override
            public void onResponse(Response response, Request.Options options, Span span) {
                standard.onResponse(response, options, span);
            }

            @Override
            public void onError(Exception exception, Request request, Span span) {
                standard.onError(exception, request, span);
            }
        };
    }
}
