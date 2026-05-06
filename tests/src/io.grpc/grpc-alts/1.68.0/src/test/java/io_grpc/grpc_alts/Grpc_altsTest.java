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
import io.grpc.ServerCredentials;
import io.grpc.Status;
import io.grpc.alts.AltsChannelBuilder;
import io.grpc.alts.AltsChannelCredentials;
import io.grpc.alts.AltsContext;
import io.grpc.alts.AltsContextUtil;
import io.grpc.alts.AltsServerBuilder;
import io.grpc.alts.AltsServerCredentials;
import io.grpc.alts.AuthorizationUtil;
import io.grpc.alts.ComputeEngineChannelCredentials;
import io.grpc.alts.GoogleDefaultChannelCredentials;
import java.io.File;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class Grpc_altsTest {
    @Test
    void altsChannelCredentialsBuilderCreatesReusableChannelCredentials() {
        ChannelCredentials defaultCredentials = AltsChannelCredentials.create();
        ChannelCredentials configuredCredentials = AltsChannelCredentials.newBuilder()
                .addTargetServiceAccount("target@example.iam.gserviceaccount.com")
                .addTargetServiceAccount("backup@example.iam.gserviceaccount.com")
                .setHandshakerAddressForTesting("localhost:1")
                .enableUntrustedAltsForTesting()
                .build();

        assertThat(defaultCredentials).isNotNull();
        assertThat(defaultCredentials.withoutBearerTokens()).isNotNull();
        assertThat(configuredCredentials).isNotNull();
        assertThat(configuredCredentials.withoutBearerTokens()).isNotNull();
    }

    @Test
    void altsServerCredentialsBuilderCreatesServerCredentials() {
        ServerCredentials defaultCredentials = AltsServerCredentials.create();
        ServerCredentials configuredCredentials = AltsServerCredentials.newBuilder()
                .setHandshakerAddressForTesting("localhost:1")
                .enableUntrustedAltsForTesting()
                .build();

        assertThat(defaultCredentials).isNotNull();
        assertThat(configuredCredentials).isNotNull();
    }

    @Test
    void googleAndComputeEngineCredentialsCanBeCreatedWithoutStartingRpc() {
        ChannelCredentials googleDefaultCredentials = GoogleDefaultChannelCredentials.newBuilder()
                .callCredentials(new NoopCallCredentials())
                .build();
        ChannelCredentials computeEngineCredentials = ComputeEngineChannelCredentials.create();

        assertThat(googleDefaultCredentials).isNotNull();
        assertThat(googleDefaultCredentials.withoutBearerTokens()).isNotNull();
        assertThat(computeEngineCredentials).isNotNull();
        assertThat(computeEngineCredentials.withoutBearerTokens()).isNotNull();
    }

    @Test
    void altsContextTestInstanceExposesPeerLocalAndSecurityLevel() {
        AltsContext context = AltsContext.createTestInstance(
                "client@example.iam.gserviceaccount.com",
                "server@example.iam.gserviceaccount.com");

        assertThat(context.getPeerServiceAccount())
                .isEqualTo("client@example.iam.gserviceaccount.com");
        assertThat(context.getLocalServiceAccount())
                .isEqualTo("server@example.iam.gserviceaccount.com");
        assertThat(context.getSecurityLevel())
                .isEqualTo(AltsContext.SecurityLevel.INTEGRITY_AND_PRIVACY);
        assertThat(AltsContext.SecurityLevel.valueOf("INTEGRITY_AND_PRIVACY"))
                .isEqualTo(AltsContext.SecurityLevel.INTEGRITY_AND_PRIVACY);
        assertThat(AltsContext.SecurityLevel.values()).containsExactly(
                AltsContext.SecurityLevel.UNKNOWN,
                AltsContext.SecurityLevel.SECURITY_NONE,
                AltsContext.SecurityLevel.INTEGRITY_ONLY,
                AltsContext.SecurityLevel.INTEGRITY_AND_PRIVACY);
    }

    @Test
    void altsContextUtilitiesRejectServerCallsWithoutAltsAttributes() {
        ServerCall<byte[], byte[]> call = new EmptyServerCall();

        assertThat(AltsContextUtil.check(call)).isFalse();
        assertThatThrownBy(() -> AltsContextUtil.createFrom(call))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No ALTS context information found");
    }

    @Test
    void authorizationCheckDeniesCallsWithoutAltsAuthenticationContext() {
        Status status = AuthorizationUtil.clientAuthorizationCheck(
                new EmptyServerCall(),
                List.of("client@example.iam.gserviceaccount.com"));

        assertThat(status.getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(status.getDescription()).contains("Peer ALTS AuthContext not found");
    }

    @Test
    void altsChannelBuilderBuildsAndTerminatesManagedChannels() throws InterruptedException {
        ManagedChannel targetChannel = AltsChannelBuilder.forTarget("localhost:1")
                .addTargetServiceAccount("target@example.iam.gserviceaccount.com")
                .enableUntrustedAltsForTesting()
                .setHandshakerAddressForTesting("localhost:1")
                .directExecutor()
                .build();
        ManagedChannel addressChannel = AltsChannelBuilder.forAddress("127.0.0.1", 1)
                .enableUntrustedAltsForTesting()
                .directExecutor()
                .build();

        try {
            assertThat(targetChannel.isShutdown()).isFalse();
            assertThat(addressChannel.isShutdown()).isFalse();
        } finally {
            targetChannel.shutdownNow();
            addressChannel.shutdownNow();
        }
        assertThat(targetChannel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(addressChannel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void altsServerBuilderBuildsServerAndRejectsTlsOverride() throws InterruptedException {
        AltsServerBuilder builder = AltsServerBuilder.forPort(0)
                .enableUntrustedAltsForTesting()
                .setHandshakerAddressForTesting("localhost:1")
                .directExecutor()
                .handshakeTimeout(1, TimeUnit.SECONDS);

        assertThatThrownBy(() -> builder.useTransportSecurity(
                new File("cert.pem"), new File("key.pem")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Can't set TLS settings for ALTS");

        Server server = builder.build();
        try {
            assertThat(server.isShutdown()).isFalse();
        } finally {
            server.shutdownNow();
        }
        assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    private static final class NoopCallCredentials extends CallCredentials {
        @Override
        public void applyRequestMetadata(
                RequestInfo requestInfo, Executor appExecutor, MetadataApplier applier) {
            appExecutor.execute(() -> applier.apply(new Metadata()));
        }
    }

    private static final class EmptyServerCall extends ServerCall<byte[], byte[]> {
        @Override
        public void request(int numMessages) {
        }

        @Override
        public void sendHeaders(Metadata headers) {
        }

        @Override
        public void sendMessage(byte[] message) {
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
        public MethodDescriptor<byte[], byte[]> getMethodDescriptor() {
            return null;
        }
    }
}
