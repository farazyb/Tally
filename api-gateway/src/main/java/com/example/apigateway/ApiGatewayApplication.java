package com.example.apigateway;

import com.example.common.logging.OpenTelemetryLoggingConfiguration;
import com.example.common.logging.RequestLoggingConfiguration;
import com.example.common.security.SecurityAuditConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({RequestLoggingConfiguration.class, SecurityAuditConfiguration.class, OpenTelemetryLoggingConfiguration.class})
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

}
