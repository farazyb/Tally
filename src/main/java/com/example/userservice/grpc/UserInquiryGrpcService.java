package com.example.userservice.grpc;

import com.example.userservice.grpc.v1.GetUserByProfileKeyRequest;
import com.example.userservice.grpc.v1.GetUserByProfileKeyResponse;
import com.example.userservice.grpc.v1.UserInquiryGrpc;
import com.example.userservice.repository.ClientRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.AllArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

/**
 * gRPC adapter for user_inquiry.proto. Extending the generated base class makes this a BindableService,
 * which is what makes Spring Boot start the gRPC server.
 */
@GrpcService
@AllArgsConstructor
public class UserInquiryGrpcService extends UserInquiryGrpc.UserInquiryImplBase {
    private final ClientRepository clientRepository;

    @Override
    public void getUserByProfileKey(GetUserByProfileKeyRequest request,
                                    StreamObserver<GetUserByProfileKeyResponse> responseObserver) {
        String profileKey = request.getProfileKey();
        if (profileKey.isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("profile_key is required")
                    .asRuntimeException());
            return;
        }

        clientRepository.getSubKeyByProfileKey(profileKey).ifPresentOrElse(
                subKey -> {
                    responseObserver.onNext(GetUserByProfileKeyResponse.newBuilder().setSubKey(subKey).build());
                    responseObserver.onCompleted();
                },
                () -> responseObserver.onError(Status.NOT_FOUND
                        .withDescription("No user with profile key " + profileKey)
                        .asRuntimeException()));
    }
}
