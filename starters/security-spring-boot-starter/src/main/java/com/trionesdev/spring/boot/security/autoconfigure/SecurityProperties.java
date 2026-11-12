package com.trionesdev.spring.boot.security.autoconfigure;

import com.trionesdev.spring.security.SecurityHttpAuthorizeRule;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties("triones.security")
public class SecurityProperties {

    private boolean csrf = false;
    private boolean anonymous = false;
    private List<String> defaultIgnoredMatchers = new ArrayList<>(List.of("/favicon.ico"));
    private List<String> ignoredMatchers = new ArrayList<>();
    private List<SecurityHttpAuthorizeRule> rules = new ArrayList<>();
}
