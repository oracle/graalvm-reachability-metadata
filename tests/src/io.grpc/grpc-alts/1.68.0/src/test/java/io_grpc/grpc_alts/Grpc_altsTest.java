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
import io.grpc.CallCredentials;
import io.grpc.ChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerCredentials;
import io.grpc.ServerInterceptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class Grpc_altsTest {
    private static final String PEER_SERVICE_ACCOUNT = "client@example.iam.gserviceaccount.com";
    private static final String LOCAL_SERVICE_ACCOUNT = "server@example.iam.gserviceaccount.com";
    private static final MethodDescriptor<String, String> TEST_METHOD = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName("alts.TestService", "Unary"))
            .setRequestMarshaller(new StringMarshaller())
            .setResponseMarshaller(new StringMarshaller())
            .build();

    @Test
    void altsContextTestInstanceExposesPeerAndLocalIdentity() {
        AltsContext context = AltsContext.createTestInstance(PEER_SERVICE_ACCOUNT, LOCAL_SERVICE_ACCOUNT);

        assertThat(context.getPeerServiceAccount()).isEqualTo(PEER_SERVICE_ACCOUNT);
        assertThat(context.getLocalServiceAccount()).isEqualTo(LOCAL_SERVICE_ACCOUNT);
        assertThat(context.getSecurityLevel()).isNotNull();
    }

    @Test
    void contextUtilityAndAuthorizationRejectServerCallsWithoutAltsAttributes() {
        ServerCall<String, String> call = new AttributeOnlyServerCall(Attributes.EMPTY);

        assertThat(AltsContextUtil.check(call)).isFalse();
        assertThatThrownBy(() -> AltsContextUtil.createFrom(call))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No ALTS context information found");

        Status status = AuthorizationUtil.clientAuthorizationCheck(call, List.of(PEER_SERVICE_ACCOUNT));
        assertThat(status.getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(status.getDescription()).contains("Peer ALTS AuthContext not found");
    }

    @Test
    void credentialFactoriesAndBuildersCreateGrpcCredentials() {
        ChannelCredentials defaultAltsCredentials = AltsChannelCredentials.create();
        ChannelCredentials configuredAltsCredentials = AltsChannelCredentials.newBuilder()
                .addTargetServiceAccount(LOCAL_SERVICE_ACCOUNT)
                .enableUntrustedAltsForTesting()
                .setHandshakerAddressForTesting("localhost:8080")
                .build();
        ChannelCredentials googleDefaultCredentials = GoogleDefaultChannelCredentials.newBuilder()
                .callCredentials(new NoopCallCredentials())
                .build();
        ChannelCredentials computeEngineCredentials = ComputeEngineChannelCredentials.create();
        ServerCredentials defaultServerCredentials = AltsServerCredentials.create();
        ServerCredentials configuredServerCredentials = AltsServerCredentials.newBuilder()
                .enableUntrustedAltsForTesting()
                .setHandshakerAddressForTesting("localhost:8080")
                .build();

        assertThat(defaultAltsCredentials).isNotNull();
        assertThat(configuredAltsCredentials).isNotNull();
        assertThat(googleDefaultCredentials).isNotNull();
        assertThat(computeEngineCredentials).isNotNull();
        assertThat(defaultServerCredentials).isNotNull();
        assertThat(configuredServerCredentials).isNotNull();
    }

    @Test
    void channelBuildersCreateClosableManagedChannels() throws InterruptedException {
        ManagedChannel altsChannel = AltsChannelBuilder.forAddress("localhost", 443)
                .addTargetServiceAccount(LOCAL_SERVICE_ACCOUNT)
                .enableUntrustedAltsForTesting()
                .setHandshakerAddressForTesting("localhost:8080")
                .directExecutor()
                .build();
        ManagedChannel googleDefaultChannel = GoogleDefaultChannelBuilder.forTarget("dns:///localhost:443")
                .directExecutor()
                .build();
        ManagedChannel computeEngineChannel = ComputeEngineChannelBuilder.forAddress("localhost", 443)
                .directExecutor()
                .build();

        try {
            assertThat(altsChannel.isShutdown()).isFalse();
            assertThat(googleDefaultChannel.isShutdown()).isFalse();
            assertThat(computeEngineChannel.isShutdown()).isFalse();
        } finally {
            shutdown(altsChannel);
            shutdown(googleDefaultChannel);
            shutdown(computeEngineChannel);
        }
    }

    @Test
    void serverBuilderConfiguresAltsServerAndRejectsTlsConfiguration() throws Exception {
        ServerServiceDefinition serviceDefinition = ServerServiceDefinition.builder("alts.TestService")
                .addMethod(TEST_METHOD, new RecordingServerCallHandler())
                .build();

        AltsServerBuilder builder = AltsServerBuilder.forPort(0)
                .enableUntrustedAltsForTesting()
                .setHandshakerAddressForTesting("localhost:8080")
                .directExecutor()
                .handshakeTimeout(5, TimeUnit.SECONDS)
                .addService(serviceDefinition)
                .intercept(new PassthroughServerInterceptor());

        assertThatThrownBy(() -> builder.useTransportSecurity(new File("cert.pem"), new File("key.pem")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Can't set TLS settings for ALTS");

        Server server = builder.build().start();
        try {
            assertThat(server.isShutdown()).isFalse();
            assertThat(server.getPort()).isPositive();
        } finally {
            server.shutdownNow();
            assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void shutdown(ManagedChannel channel) throws InterruptedException {
        channel.shutdownNow();
        assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    private static final class NoopCallCredentials extends CallCredentials {
        @Override
        public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, MetadataApplier applier) {
            appExecutor.execute(() -> applier.apply(new Metadata()));
        }
    }

    private static final class AttributeOnlyServerCall extends ServerCall<String, String> {
        private final Attributes attributes;

        private AttributeOnlyServerCall(Attributes attributes) {
            this.attributes = attributes;
        }

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
        public MethodDescriptor<String, String> getMethodDescriptor() {
            return TEST_METHOD;
        }

        @Override
        public Attributes getAttributes() {
            return attributes;
        }
    }

    private static final class RecordingServerCallHandler implements ServerCallHandler<String, String> {
        @Override
        public ServerCall.Listener<String> startCall(ServerCall<String, String> call, Metadata headers) {
            return new ServerCall.Listener<>() {
            };
        }
    }

    private static final class PassthroughServerInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            return next.startCall(call, headers);
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
                throw new UncheckedIOException(e);
            }
        }
    }
}
