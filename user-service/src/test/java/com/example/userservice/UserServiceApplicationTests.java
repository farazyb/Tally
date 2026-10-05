package com.example.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.grpc.server.port=0") // random port: don't collide with a running user-service
class UserServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
