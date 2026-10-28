/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_api;

import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises gRPC's metadata and server interceptor authentication subsystem for shipment tracking.
public class MetadataAuthenticationTest {
    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> AUTHENTICATION_HINT =
            Metadata.Key.of("authentication-hint", Metadata.ASCII_STRING_MARSHALLER);
    private static final String SERVICE_NAME = "shipping.TrackingService";
    private static final MethodDescriptor<StringValue, StringValue> TRACK =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "Track"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    @Test
    @Timeout(60)
    public void requiresBearerCredentialsBeforeReturningShipmentDetails() throws Exception {
        ShipmentService shipments = new ShipmentService(Map.of("parcel-104", "Out for delivery"));
        Server server = ServerBuilder.forPort(0)
                .addService(ServerInterceptors.intercept(shipments.bindService(),
                        new BearerAuthenticationInterceptor("shipping-client-secret")))
                .build().start();
        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                    .usePlaintext()
                    .build();

            ManagedChannel unauthenticatedChannel = channel;
            StatusRuntimeException rejected = assertThrows(StatusRuntimeException.class,
                    () -> track(unauthenticatedChannel, "parcel-104"));
            assertEquals(Status.Code.UNAUTHENTICATED, rejected.getStatus().getCode());
            assertEquals("A valid shipping bearer token is required", rejected.getStatus().getDescription());
            assertNotNull(rejected.getTrailers());
            assertEquals("Supply an authorization bearer token", rejected.getTrailers().get(AUTHENTICATION_HINT));

            Metadata credentials = new Metadata();
            credentials.put(AUTHORIZATION, "Bearer shipping-client-secret");
            Channel authenticatedChannel = ClientInterceptors.intercept(channel,
                    MetadataUtils.newAttachHeadersInterceptor(credentials));
            assertEquals("Out for delivery", track(authenticatedChannel, "parcel-104"));

            Metadata invalidCredentials = new Metadata();
            invalidCredentials.put(AUTHORIZATION, "Bearer revoked-token");
            Channel revokedChannel = ClientInterceptors.intercept(channel,
                    MetadataUtils.newAttachHeadersInterceptor(invalidCredentials));
            StatusRuntimeException revoked = assertThrows(StatusRuntimeException.class,
                    () -> track(revokedChannel, "parcel-104"));
            assertEquals(Status.Code.UNAUTHENTICATED, revoked.getStatus().getCode());

            // Rejection of another caller must not affect an authenticated caller on the same connection.
            assertEquals("Out for delivery", track(authenticatedChannel, "parcel-104"));
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
            server.shutdownNow();
            if (channel != null) {
                assertTrue(channel.awaitTermination(10, TimeUnit.SECONDS), "Client channel did not terminate");
            }
            assertTrue(server.awaitTermination(10, TimeUnit.SECONDS), "Shipping server did not terminate");
        }
    }

    private static String track(Channel channel, String shipmentId) {
        return ClientCalls.blockingUnaryCall(channel, TRACK,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), StringValue.of(shipmentId)).getValue();
    }

    private static final class BearerAuthenticationInterceptor implements ServerInterceptor {
        private final String expectedAuthorization;

        private BearerAuthenticationInterceptor(String token) {
            this.expectedAuthorization = "Bearer " + token;
        }

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            if (!expectedAuthorization.equals(headers.get(AUTHORIZATION))) {
                Metadata trailers = new Metadata();
                trailers.put(AUTHENTICATION_HINT, "Supply an authorization bearer token");
                call.close(Status.UNAUTHENTICATED.withDescription("A valid shipping bearer token is required"),
                        trailers);
                return new ServerCall.Listener<ReqT>() { };
            }
            return next.startCall(call, headers);
        }
    }

    private static final class ShipmentService {
        private final Map<String, String> shipments;

        private ShipmentService(Map<String, String> shipments) {
            this.shipments = Map.copyOf(shipments);
        }

        private ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE_NAME)
                    .addMethod(TRACK, ServerCalls.asyncUnaryCall(this::track))
                    .build();
        }

        private void track(StringValue request, StreamObserver<StringValue> response) {
            response.onNext(StringValue.of(shipments.getOrDefault(request.getValue(), "Unknown shipment")));
            response.onCompleted();
        }
    }
}
