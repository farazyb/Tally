package com.example.userservice;

import com.example.userservice.grpc.v1.GetUserByProfileKeyRequest;
import com.example.userservice.grpc.v1.UserInquiryGrpc;
import com.example.userservice.model.Client;
import com.example.userservice.repository.ClientRepository;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Calls UserInquiry through a real gRPC channel (in-process transport, so no network port is needed)
 * against the Testcontainers database.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "spring.grpc.server.inprocess.name=" + UserInquiryGrpcServiceTest.SERVER_NAME,
        "spring.grpc.server.port=0"
})
class UserInquiryGrpcServiceTest {
    static final String SERVER_NAME = "user-inquiry-test";
    private static final String TOKEN = "service-token";

    @Autowired
    private ClientRepository clientRepository;
    // gRPC serves only services on the private network, so LocalGrpcSecurityConfig permits all calls.
    // The other tests still send a bearer token; the decoder is mocked so no running Keycloak is needed.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private ManagedChannel channel;
    private UserInquiryGrpc.UserInquiryBlockingStub userInquiry;

    @BeforeEach
    void setUp() {
        clientRepository.deleteAll();
        when(jwtDecoder.decode(TOKEN)).thenReturn(Jwt.withTokenValue(TOKEN)
                .header("alg", "RS256")
                .subject("wallet-service")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build());

        channel = InProcessChannelBuilder.forName(SERVER_NAME).directExecutor().build();
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + TOKEN);
        userInquiry = UserInquiryGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    // gRPC is private-network only (LocalGrpcSecurityConfig permits all), so a call without a token is served.
    @Test
    void callWithoutTokenIsPermitted() {
        clientRepository.saveAndFlush(Client.builder().subKey("4f1c2b8e-sub").profileKey("q0U").build());
        var anonymous = UserInquiryGrpc.newBlockingStub(channel);

        var response = anonymous.getUserByProfileKey(request("q0U"));

        assertThat(response.getSubKey()).isEqualTo("4f1c2b8e-sub");
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void knownProfileKeyReturnsSubKey() {
        clientRepository.saveAndFlush(Client.builder().subKey("4f1c2b8e-sub").profileKey("q0U").build());

        var response = userInquiry.getUserByProfileKey(request("q0U"));

        assertThat(response.getSubKey()).isEqualTo("4f1c2b8e-sub");
    }

    @Test
    void unknownProfileKeyFailsWithNotFound() {
        assertThatThrownBy(() -> userInquiry.getUserByProfileKey(request("nope")))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND));
    }

    @Test
    void emptyProfileKeyFailsWithInvalidArgument() {
        assertThatThrownBy(() -> userInquiry.getUserByProfileKey(request("")))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    private static GetUserByProfileKeyRequest request(String profileKey) {
        return GetUserByProfileKeyRequest.newBuilder().setProfileKey(profileKey).build();
    }
}
