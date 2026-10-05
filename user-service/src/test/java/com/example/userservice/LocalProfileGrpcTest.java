package com.example.userservice;

import com.example.userservice.grpc.v1.GetUserByProfileKeyRequest;
import com.example.userservice.grpc.v1.UserInquiryGrpc;
import com.example.userservice.model.Client;
import com.example.userservice.repository.ClientRepository;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With the "local" profile, gRPC calls work without a token. UserInquiryGrpcServiceTest covers the default
 * (secured) behavior.
 */
@ActiveProfiles("local")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "spring.grpc.server.inprocess.name=" + LocalProfileGrpcTest.SERVER_NAME,
        "spring.grpc.server.port=0"
})
class LocalProfileGrpcTest {
    static final String SERVER_NAME = "local-profile-test";

    @Autowired
    private ClientRepository clientRepository;

    private final ManagedChannel channel = InProcessChannelBuilder.forName(SERVER_NAME).directExecutor().build();

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void callWithoutTokenSucceeds() {
        clientRepository.deleteAll();
        clientRepository.saveAndFlush(Client.builder().subKey("local-sub").profileKey("abc").build());

        var response = UserInquiryGrpc.newBlockingStub(channel)
                .getUserByProfileKey(GetUserByProfileKeyRequest.newBuilder().setProfileKey("abc").build());

        assertThat(response.getSubKey()).isEqualTo("local-sub");
    }
}
