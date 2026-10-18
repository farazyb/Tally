package com.example.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SecurityAuditConfiguration {

    @Bean
    AuditingAuthenticationEntryPoint auditingAuthenticationEntryPoint() {
        return new AuditingAuthenticationEntryPoint();
    }

    @Bean
    AuditingAccessDeniedHandler auditingAccessDeniedHandler() {
        return new AuditingAccessDeniedHandler();
    }
}
