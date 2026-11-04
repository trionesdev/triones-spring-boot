package com.trionesdev.spring.boot.lock.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "triones.lock")
public class LockProperties {
    private LockType type = LockType.THREAD;
}
