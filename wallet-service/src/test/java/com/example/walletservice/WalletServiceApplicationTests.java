package com.example.walletservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// Listeners stay stopped so the test never consumes from a locally running Kafka.
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(TestcontainersConfiguration.class)
class WalletServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
