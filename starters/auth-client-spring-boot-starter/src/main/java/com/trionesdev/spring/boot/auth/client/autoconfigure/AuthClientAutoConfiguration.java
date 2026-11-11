package com.trionesdev.spring.boot.auth.client.autoconfigure;

import com.trionesdev.spring.security.auth.client.AuthorizedClientManager;
import com.trionesdev.spring.security.auth.client.ClientCredentialsTokenResponseClient;
import com.trionesdev.spring.security.auth.client.ClientRegistrationRepository;
import com.trionesdev.spring.security.auth.client.InMemoryClientRegistrationRepository;
import com.trionesdev.spring.security.auth.client.OkHttpClientCredentialsTokenResponseClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Collections;
import java.util.Map;

/**
 * 根据 {@code triones.security.auth.client.*} 创建服务间调用所需的
 * {@link ClientRegistrationRepository} 与 {@link AuthorizedClientManager}。
 * <p>
 * 不依赖 Web 环境，定时任务、MQ 消费者等非 Web 应用同样可用。
 * 仅当至少配置了一个 registration 时生效；各 bean 均可由使用方覆盖。
 */
@AutoConfiguration
@ConditionalOnClass(AuthorizedClientManager.class)
@EnableConfigurationProperties(AuthClientProperties.class)
public class AuthClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RegistrationsConfiguredCondition.class)
    public ClientRegistrationRepository clientRegistrationRepository(AuthClientProperties properties) {
        return new InMemoryClientRegistrationRepository(AuthClientPropertiesMapper.toRegistrations(properties));
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RegistrationsConfiguredCondition.class)
    public ClientCredentialsTokenResponseClient clientCredentialsTokenResponseClient() {
        return new OkHttpClientCredentialsTokenResponseClient();
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(RegistrationsConfiguredCondition.class)
    public AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository clientRegistrationRepository,
                                                           ClientCredentialsTokenResponseClient tokenResponseClient,
                                                           AuthClientProperties properties) {
        AuthorizedClientManager manager = new AuthorizedClientManager(clientRegistrationRepository, tokenResponseClient);
        if (properties.getClockSkew() != null) {
            manager.setClockSkew(properties.getClockSkew());
        }
        return manager;
    }

    /**
     * 存在 {@code triones.security.auth.client.registration.*} 配置时匹配。
     */
    static class RegistrationsConfiguredCondition extends SpringBootCondition {
        private static final Bindable<Map<String, AuthClientProperties.Registration>> REGISTRATIONS =
                Bindable.mapOf(String.class, AuthClientProperties.Registration.class);

        @Override
        public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Map<String, AuthClientProperties.Registration> registrations = Binder.get(context.getEnvironment())
                    .bind("triones.security.auth.client.registration", REGISTRATIONS)
                    .orElse(Collections.emptyMap());
            return registrations.isEmpty()
                    ? ConditionOutcome.noMatch("no triones.security.auth.client.registration configured")
                    : ConditionOutcome.match("triones.security.auth.client.registration configured");
        }
    }
}
