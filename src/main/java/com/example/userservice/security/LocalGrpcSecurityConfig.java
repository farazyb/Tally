package com.example.userservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor;
import org.springframework.grpc.server.security.GrpcSecurity;

@Configuration
public class LocalGrpcSecurityConfig {

    @Bean
    @GlobalServerInterceptor
    AuthenticationProcessInterceptor permitAllGrpcCalls(GrpcSecurity grpc) throws Exception {
        return grpc.authorizeRequests(requests -> requests.allRequests().permitAll()).build();
    }
}
