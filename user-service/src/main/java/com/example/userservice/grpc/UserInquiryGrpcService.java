package com.example.userservice.grpc;

import com.example.userservice.grpc.v1.GetUserByProfileKeyRequest;
import com.example.userservice.grpc.v1.GetUserByProfileKeyResponse;
import com.example.userservice.grpc.v1.UserInquiryGrpc;
import com.example.userservice.repository.ClientRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

/**
 * gRPC adapter for user_inquiry.proto. Extending the generated base class makes this a BindableService,
 * which is what makes Spring Boot start the gRPC server.
 */
@Slf4j
@GrpcService
@AllArgsConstructor
public class UserInquiryGrpcService extends UserInquiryGrpc.UserInquiryImplBase {
    private final ClientRepository clientRepository;

    @Override
    public void getUserByProfileKey(GetUserByProfileKeyRequest request,
                                    StreamObserver<GetUserByProfileKeyResponse> responseObserver) {
        String profileKey = request.getProfileKey();
        if (profileKey.isBlank()) {
            log.debug("GetUserByProfileKey rejected: empty profile key");
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("profile_key is required")
                    .asRuntimeException());
            return;
        }

        clientRepository.getSubKeyByProfileKey(profileKey).ifPresentOrElse(
                subKey -> {
                    log.atDebug().addKeyValue("profileKey", profileKey).log("GetUserByProfileKey resolved");
                    responseObserver.onNext(GetUserByProfileKeyResponse.newBuilder().setSubKey(subKey).build());
                    responseObserver.onCompleted();
                },
                () -> {
                    log.atDebug().addKeyValue("profileKey", profileKey).log("GetUserByProfileKey: no user found");
                    responseObserver.onError(Status.NOT_FOUND
                            .withDescription("No user with profile key " + profileKey)
                            .asRuntimeException());
                });
    }
}
