/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_alts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.Attributes;
import io.grpc.BindableService;
import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.ChannelCredentials;
import io.grpc.ClientCall;
import io.grpc.CompressorRegistry;
import io.grpc.DecompressorRegistry;
import io.grpc.ForwardingServerCall;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerCredentials;
import io.grpc.ServerInterceptor;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServerTransportFilter;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.alts.AltsChannelBuilder;
import io.grpc.alts.AltsChannelCredentials;
import io.grpc.alts.AltsContext;
import io.grpc.alts.AltsContextUtil;
import io.grpc.alts.AltsServerBuilder;
import io.grpc.alts.AltsServerCredentials;
import io.grpc.alts.AuthorizationUtil;
import io.grpc.alts.ComputeEngineChannelBuilder;
import io.grpc.alts.ComputeEngineChannelCredentials;
import io.grpc.alts.GoogleDefaultChannelBuilder;
import io.grpc.alts.GoogleDefaultChannelCredentials;
import io.grpc.stub.ClientCalls;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class Grpc_altsTest {
    private static final MethodDescriptor<String, String> UNARY_METHOD = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName("alts.TestService", "Echo"))
            .setRequestMarshaller(new StringMarshaller())
            .setResponseMarshaller(new StringMarshaller())
            .build();

    @Test
    void createsAllCredentialVariants() {
        CallCredentials callCredentials = new SuccessfulCallCredentials();

        ChannelCredentials altsCredentials = AltsChannelCredentials.create();
        ChannelCredentials configuredAltsCredentials = AltsChannelCredentials.newBuilder()
                .addTargetServiceAccount("target@example.iam.gserviceaccount.com")
                .enableUntrustedAltsForTesting()
                .setHandshakerAddressForTesting("127.0.0.1:1")
                .build();
        ServerCredentials altsServerCredentials = AltsServerCredentials.create();
        ServerCredentials configuredServerCredentials = AltsServerCredentials.newBuilder()
                .enableUntrustedAltsForTesting()
                .setHandshakerAddressForTesting("127.0.0.1:1")
                .build();
        ChannelCredentials googleDefaultCredentials = GoogleDefaultChannelCredentials.newBuilder()
                .callCredentials(callCredentials)
                .altsCallCredentials(new SuccessfulCallCredentials())
                .build();
        ChannelCredentials computeEngineCredentials = ComputeEngineChannelCredentials.create();

        assertThat(altsCredentials).isNotNull();
        assertThat(altsCredentials.withoutBearerTokens()).isNotNull();
        assertThat(configuredAltsCredentials).isNotNull();
        assertThat(configuredAltsCredentials.withoutBearerTokens()).isNotNull();
        assertThat(altsServerCredentials).isNotNull();
        assertThat(configuredServerCredentials).isNotNull();
        assertThat(googleDefaultCredentials).isNotNull();
        assertThat(googleDefaultCredentials.withoutBearerTokens()).isNotNull();
        assertThat(GoogleDefaultChannelCredentials.create()).isNotNull();
        assertThat(computeEngineCredentials).isNotNull();
        assertThat(computeEngineCredentials.withoutBearerTokens()).isNotNull();
    }

    @Test
    void altsContextExposesTestPeerAndLocalIdentity() {
        AltsContext context = AltsContext.createTestInstance(
                "peer@example.iam.gserviceaccount.com",
                "local@example.iam.gserviceaccount.com");

        assertThat(context.getPeerServiceAccount()).isEqualTo("peer@example.iam.gserviceaccount.com");
        assertThat(context.getLocalServiceAccount()).isEqualTo("local@example.iam.gserviceaccount.com");
        assertThat(context.getSecurityLevel()).isEqualTo(AltsContext.SecurityLevel.INTEGRITY_AND_PRIVACY);
        assertThat(AltsContext.SecurityLevel.valueOf("INTEGRITY_AND_PRIVACY"))
                .isEqualTo(AltsContext.SecurityLevel.INTEGRITY_AND_PRIVACY);
        assertThat(AltsContext.SecurityLevel.values()).contains(
                AltsContext.SecurityLevel.UNKNOWN,
                AltsContext.SecurityLevel.SECURITY_NONE,
                AltsContext.SecurityLevel.INTEGRITY_ONLY,
                AltsContext.SecurityLevel.INTEGRITY_AND_PRIVACY);
    }

    @Test
    void altsContextUtilitiesRejectCallsWithoutAltsAttributes() {
        ServerCall<String, String> serverCall = new PlainServerCall();
        ClientCall<String, String> clientCall = new PlainClientCall();

        assertThat(AltsContextUtil.check(Attributes.EMPTY)).isFalse();
        assertThat(AltsContextUtil.check(serverCall)).isFalse();
        assertThat(AltsContextUtil.check(clientCall)).isFalse();
        assertThatThrownBy(() -> AltsContextUtil.createFrom(Attributes.EMPTY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No ALTS context information found");
        assertThatThrownBy(() -> AltsContextUtil.createFrom(serverCall))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No ALTS context information found");
        assertThatThrownBy(() -> AltsContextUtil.createFrom(clientCall))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No ALTS context information found");

        Status authorizationStatus = AuthorizationUtil.clientAuthorizationCheck(
                serverCall,
                List.of("peer@example.iam.gserviceaccount.com"));
        assertThat(authorizationStatus.getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(authorizationStatus.getDescription()).contains("Peer ALTS AuthContext not found");
    }

    @Test
    void altsServerBuilderStartsWithConfiguredForwardedOptions() throws IOException, InterruptedException {
        Server server = AltsServerBuilder.forPort(0)
                .enableUntrustedAltsForTesting()
                .setHandshakerAddressForTesting("127.0.0.1:1")
                .directExecutor()
                .handshakeTimeout(1, TimeUnit.SECONDS)
                .compressorRegistry(CompressorRegistry.getDefaultInstance())
                .decompressorRegistry(DecompressorRegistry.getDefaultInstance())
                .addTransportFilter(new ServerTransportFilter() {
                })
                .build();

        try {
            server.start();
            assertThat(server.getPort()).isGreaterThan(0);
        } finally {
            shutdownServer(server);
        }
    }

    @Test
    void altsServerBuilderRejectsTlsConfiguration() {
        AltsServerBuilder builder = AltsServerBuilder.forPort(0);

        assertThatThrownBy(() -> builder.useTransportSecurity(new File("cert.pem"), new File("key.pem")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("ALTS");
    }

    @Test
    void altsServerBuilderRegistersBindableServices() throws IOException, InterruptedException {
        Server server = AltsServerBuilder.forPort(0)
                .enableUntrustedAltsForTesting()
                .setHandshakerAddressForTesting("127.0.0.1:1")
                .directExecutor()
                .addService(new EchoBindableService())
                .build();

        try {
            server.start();

            ServerServiceDefinition service = server.getServices().stream()
                    .filter(serviceDefinition -> "alts.TestService".equals(
                            serviceDefinition.getServiceDescriptor().getName()))
                    .findFirst()
                    .orElseThrow();
            assertThat(service.getMethod(UNARY_METHOD.getFullMethodName())).isNotNull();
        } finally {
            shutdownServer(server);
        }
    }

    @Test
    void altsServerBuilderAcceptsInterceptorsWhileServingRegisteredServices() throws IOException, InterruptedException {
        ServerInterceptor responsePrefixInterceptor = new ResponsePrefixingInterceptor();
        Server server = AltsServerBuilder.forPort(0)
                .enableUntrustedAltsForTesting()
                .setHandshakerAddressForTesting("127.0.0.1:1")
                .directExecutor()
                .intercept(responsePrefixInterceptor)
                .addService(new EchoBindableService())
                .build();

        try {
            server.start();

            ServerServiceDefinition service = server.getServices().stream()
                    .filter(serviceDefinition -> "alts.TestService".equals(
                            serviceDefinition.getServiceDescriptor().getName()))
                    .findFirst()
                    .orElseThrow();
            @SuppressWarnings("unchecked")
            ServerMethodDefinition<String, String> method = (ServerMethodDefinition<String, String>) service.getMethod(
                    UNARY_METHOD.getFullMethodName());
            RecordingServerCall call = new RecordingServerCall();

            method.getServerCallHandler().startCall(call, new Metadata());

            assertThat(call.getMessages()).containsExactly("response");
            assertThat(call.getStatus().getCode()).isEqualTo(Status.Code.OK);
        } finally {
            shutdownServer(server);
        }
    }

    @Test
    void altsChannelBuilderProducesBoundedRpcFailureWhenHandshakeCannotComplete() throws InterruptedException {
        ManagedChannel channel = AltsChannelBuilder.forAddress("127.0.0.1", 1)
                .addTargetServiceAccount("target@example.iam.gserviceaccount.com")
                .enableUntrustedAltsForTesting()
                .setHandshakerAddressForTesting("127.0.0.1:1")
                .directExecutor()
                .build();

        try {
            assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(
                    channel,
                    UNARY_METHOD,
                    CallOptions.DEFAULT.withDeadlineAfter(2, TimeUnit.SECONDS),
                    "request"))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(error -> assertThat(((StatusRuntimeException) error).getStatus().getCode())
                            .isNotEqualTo(Status.Code.OK));
        } finally {
            shutdownChannel(channel);
        }
    }

    @Test
    void googleDefaultAndComputeEngineBuildersCreateManagedChannels() throws InterruptedException {
        ManagedChannel googleDefaultChannel = null;
        ManagedChannel computeEngineChannel = null;
        try {
            googleDefaultChannel = GoogleDefaultChannelBuilder.forAddress("127.0.0.1", 1)
                    .directExecutor()
                    .build();
            computeEngineChannel = ComputeEngineChannelBuilder.forTarget("dns:///localhost:1")
                    .directExecutor()
                    .build();

            assertThat(googleDefaultChannel).isNotNull();
            assertThat(computeEngineChannel).isNotNull();
        } finally {
            shutdownChannelIfCreated(googleDefaultChannel);
            shutdownChannelIfCreated(computeEngineChannel);
        }
    }

    private static void shutdownServer(Server server) throws InterruptedException {
        server.shutdownNow();
        assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    private static void shutdownChannel(ManagedChannel channel) throws InterruptedException {
        channel.shutdownNow();
        assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    private static void shutdownChannelIfCreated(ManagedChannel channel) throws InterruptedException {
        if (channel != null) {
            shutdownChannel(channel);
        }
    }

    private static final class SuccessfulCallCredentials extends CallCredentials {
        @Override
        public void applyRequestMetadata(RequestInfo requestInfo, Executor executor, MetadataApplier applier) {
            executor.execute(() -> applier.apply(new Metadata()));
        }
    }

    private static final class StringMarshaller implements MethodDescriptor.Marshaller<String> {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to parse string payload", e);
            }
        }
    }

    private static final class EchoBindableService implements BindableService {
        @Override
        public ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder("alts.TestService")
                    .addMethod(UNARY_METHOD, new EchoServerCallHandler())
                    .build();
        }
    }

    private static final class EchoServerCallHandler implements ServerCallHandler<String, String> {
        @Override
        public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
            call.sendHeaders(new Metadata());
            call.sendMessage("response");
            call.close(Status.OK, new Metadata());
            return new ServerCall.Listener<>() {
            };
        }
    }

    private static final class ResponsePrefixingInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call,
                Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {
            return next.startCall(new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                @Override
                public void sendMessage(RespT message) {
                    @SuppressWarnings("unchecked")
                    RespT prefixedMessage = (RespT) ("intercepted:" + message);
                    super.sendMessage(prefixedMessage);
                }
            }, headers);
        }
    }

    private static final class PlainServerCall extends ServerCall<String, String> {
        @Override
        public void request(int numMessages) {
        }

        @Override
        public void sendHeaders(Metadata headers) {
        }

        @Override
        public void sendMessage(String message) {
        }

        @Override
        public void close(Status status, Metadata trailers) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public Attributes getAttributes() {
            return Attributes.EMPTY;
        }

        @Override
        public MethodDescriptor<String, String> getMethodDescriptor() {
            return UNARY_METHOD;
        }
    }

    private static final class RecordingServerCall extends ServerCall<String, String> {
        private final List<String> messages = new ArrayList<>();
        private Status status = Status.UNKNOWN;

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void sendHeaders(Metadata headers) {
        }

        @Override
        public void sendMessage(String message) {
            messages.add(message);
        }

        @Override
        public void close(Status status, Metadata trailers) {
            this.status = status;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public Attributes getAttributes() {
            return Attributes.EMPTY;
        }

        @Override
        public MethodDescriptor<String, String> getMethodDescriptor() {
            return UNARY_METHOD;
        }

        List<String> getMessages() {
            return messages;
        }

        Status getStatus() {
            return status;
        }
    }

    private static final class PlainClientCall extends ClientCall<String, String> {
        @Override
        public void start(Listener<String> responseListener, Metadata headers) {
            responseListener.onClose(Status.OK, new Metadata());
        }

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void cancel(String message, Throwable cause) {
        }

        @Override
        public void halfClose() {
        }

        @Override
        public void sendMessage(String message) {
        }

        @Override
        public Attributes getAttributes() {
            return Attributes.EMPTY;
        }
    }
}
