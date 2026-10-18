package com.example.userservice;

import com.example.common.logging.RequestLoggingConfiguration;
import com.example.common.outbox.OutboxConfiguration;
import com.example.common.security.SecurityAuditConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({RequestLoggingConfiguration.class, SecurityAuditConfiguration.class, OutboxConfiguration.class})
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }

}
