package com.example.walletservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // Same database and role as docker/postgres/init.sh creates for wallet-service.
        return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"))
                .withDatabaseName("wallet_db")
                .withUsername("wallet_svc");
    }

}
