package com.example.userservice;

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
        // Same role/database as docker/postgres/init.sql: V1 runs "alter table client owner to user_svc".
        return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"))
                .withDatabaseName("user_db")
                .withUsername("user_svc");
    }

}
