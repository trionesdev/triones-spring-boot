package com.trionesdev.spring.boot.context.autoconfigure;

import com.trionesdev.commons.context.actor.ActorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@RequiredArgsConstructor
@Configuration
public class ContextAutoConfiguration {

    @Bean
    public ActorContext operateContext(){
        return new ActorContext();
    }
}
