package com.trionesdev.spring.boot.web.autoconfigure.version;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;

public class ApiVersionCondition extends SpringBootCondition {
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String sourceClass = "";
        if (metadata instanceof ClassMetadata classMetadata) {
            sourceClass = classMetadata.getClassName();
        }
        ConditionMessage.Builder message = ConditionMessage.forCondition("apiVersion", sourceClass);
        Environment environment = context.getEnvironment();
        try {
            BindResult<ApiVersionType> specified = Binder.get(environment).bind("triones.web.api-version.type", ApiVersionType.class);
            ApiVersionType required = ApiVersionConfigurations.getType(((AnnotationMetadata) metadata).getClassName());
            if (specified.isBound() && specified.get() == required) {
                return ConditionOutcome.match(message.because(specified.get() + " apiVersion type"));
            }
        } catch (BindException ex) {
            // Ignore
        }
        return ConditionOutcome.noMatch(message.because("unknown lock type"));
    }
}
