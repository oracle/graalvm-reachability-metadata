/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auth.oauth2.ServiceAccountCredentials;
import io.grpc.Attributes;
import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.SecurityLevel;
import io.grpc.Status;
import io.grpc.auth.MoreCallCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class GoogleAuthLibraryCallCredentialsInnerJwtHelperTest {
    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of(
            "authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> QUOTA_PROJECT = Metadata.Key.of(
            "x-goog-user-project", Metadata.ASCII_STRING_MARSHALLER);
    private static final String TEST_PRIVATE_KEY = """
            -----BEGIN PRIVATE KEY-----
            MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBALxhg8+U6jJ09nd/
            H9ctKh68PIQOprlNMHaGYj3t7+chAbPQKT45u8uTiJdxh6AQgF1xGqtRaJjpGAv/
            wCwLTWdPV64w7NnwgZKFVKydXyhZ/pYUb9qEUtKYbqLJQb4LTplgK4g3FPuwxG9P
            MPX6wUdX0eaSMLh1/0goQ2Acl7M7AgMBAAECgYEAsRKsltwwhDtDTbH2GQpANGHW
            NH+dIkM3UcIh6HqW7dgtxHrfubkFGGfWlhxGUyQPtUXSvsSoIEvCqcy+M+hv2RLo
            ylWltbgNm71n0MYyR/Lk76VlyZm4dmuDlO4g+13HnLjhX8Wyms0cXgMLBfyBErWi
            sBxZzKqxnP+LcsZtlXECQQD6YvZ+svzcYopIaKxHzRBX9uR3Pwz5zz/ZpMshv4Wm
            mR4XVuN3pKb0YSqY2xLgD6r2StJPReCLzuuMymXVSR+DAkEAwJqvQAdgC20fKAIl
            XonPnJyyPXnhPrkdKmcnAexfxOMd43hQn/ZfkRGTh9VulQCc5cEeLkXCrnvQF4mS
            7rHX6QJBAJBzYYPM3SaDzwVhJesyefhVno7RGDpSf6KfePVaWc+4TGzomshefFdG
            a13OHYCYaWg32EcVqCrgUHJkHgXDbuMCQDHjHwd3oZH0FsDXJXTCP0sybxxrSENl
            0T8dbsYZwoiNxz0W+dMlDoXVxp/FUZo4iWsdRUL5AL8PBMxD7xHycJkCQQCFjI54
            GWnZuwg6LdXMS7VHFqVPjJ+mig7oEPqh55JekhURE16p0EN0EFdAYGg0th3n0a+g
            vZHfD8ywUMayOu0A
            -----END PRIVATE KEY-----
            """;

    @Test
    void serviceAccountWithoutScopesUsesJwtAccessCredentials() throws Exception {
        ServiceAccountCredentials credentials = ServiceAccountCredentials.newBuilder()
                .setClientId("client-id")
                .setClientEmail("jwt-helper@example.iam.gserviceaccount.com")
                .setPrivateKey(privateKey())
                .setPrivateKeyId("private-key-id")
                .setQuotaProjectId("quota-project")
                .setHttpTransportFactory(() -> {
                    throw new UnsupportedOperationException(
                            "JWT access credentials do not use HTTP");
                })
                .build();
        CallCredentials callCredentials = MoreCallCredentials.from(credentials);
        CapturingApplier applier = new CapturingApplier();

        callCredentials.applyRequestMetadata(
                new TestRequestInfo(), Runnable::run, applier);

        Metadata headers = applier.awaitHeaders();
        assertThat(headers.get(AUTHORIZATION)).startsWith("Bearer ");
        assertThat(headers.get(QUOTA_PROJECT)).isEqualTo("quota-project");
        assertThat(applier.failure()).isNull();
    }

    private static PrivateKey privateKey() throws Exception {
        String encodedKey = TEST_PRIVATE_KEY
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private static final class CapturingApplier extends CallCredentials.MetadataApplier {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<Metadata> headers = new AtomicReference<>();
        private final AtomicReference<Status> failure = new AtomicReference<>();

        @Override
        public void apply(Metadata headers) {
            this.headers.set(headers);
            latch.countDown();
        }

        @Override
        public void fail(Status status) {
            failure.set(status);
            latch.countDown();
        }

        private Metadata awaitHeaders() throws InterruptedException {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            assertThat(headers.get()).isNotNull();
            return headers.get();
        }

        private Status failure() {
            return failure.get();
        }
    }

    private static final class TestRequestInfo extends CallCredentials.RequestInfo {
        private static final MethodDescriptor<byte[], byte[]> METHOD = MethodDescriptor
                .<byte[], byte[]>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName("jwt.Service", "Call"))
                .setRequestMarshaller(new ByteArrayMarshaller())
                .setResponseMarshaller(new ByteArrayMarshaller())
                .build();

        @Override
        public MethodDescriptor<?, ?> getMethodDescriptor() {
            return METHOD;
        }

        @Override
        public CallOptions getCallOptions() {
            return CallOptions.DEFAULT;
        }

        @Override
        public SecurityLevel getSecurityLevel() {
            return SecurityLevel.PRIVACY_AND_INTEGRITY;
        }

        @Override
        public String getAuthority() {
            return "example.googleapis.com";
        }

        @Override
        public Attributes getTransportAttrs() {
            return Attributes.EMPTY;
        }
    }

    private static final class ByteArrayMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            try {
                return stream.readAllBytes();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
