/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class GoogleAuthLibraryCallCredentialsInnerJwtHelperTest {
    private static final Metadata.Key<String> AUTHORIZATION_HEADER =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final MethodDescriptor.Marshaller<byte[]> BYTE_ARRAY_MARSHALLER =
            new MethodDescriptor.Marshaller<byte[]>() {
                @Override
                public InputStream stream(byte[] value) {
                    return new ByteArrayInputStream(value);
                }

                @Override
                public byte[] parse(InputStream stream) {
                    try {
                        return stream.readAllBytes();
                    } catch (IOException exception) {
                        throw new IllegalStateException(
                                "Unable to parse test method payload", exception);
                    }
                }
            };
    private static final MethodDescriptor<byte[], byte[]> METHOD =
            MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(
                            "grpc.testing.TestService", "Unary"))
                    .setRequestMarshaller(BYTE_ARRAY_MARSHALLER)
                    .setResponseMarshaller(BYTE_ARRAY_MARSHALLER)
                    .build();
    private static final String TEST_PRIVATE_KEY = """
            -----BEGIN PRIVATE KEY-----
            MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBALl+iMS7xzg7yy8a
            +0nTJVEEbIWA9fkw8LNm6hM080r2ODpcgul2hyZ2H8WWVygO+1tmWPzSh2wqWpCF
            RfUiEB4f0WPuLLuF1rF7iDbra/s8g/uDpEKurKTYa8xZgXh9Vt63R2/pxBAsA3Nb
            KPs1MSbuMeR6lbXZoWdmzIbUkvNBAgMBAAECgYEAijIlPz0PDWwu0mdts/ClKpj7
            KPONThwSF/aiibC3Wa/a8FAgEEOIKTV0MkbxpByVU+EKh0FqN0HOu0Evf5PjIOdA
            zOW9BMx23oILc5l0DQixWoSr9q9avtfDf6ZZV2WqOK5+kUk0aQ0x9WBpRDD3x5aE
            H6Yt1BcRFLG524zCTOECQQD2LQmtNwZX98hc0Qk4Zu8MSiENtQ7i9H+ixvBShCyv
            Ls5mhGtHCjEoMbz2u32qxkliE1SObHBa9OdFOVDebIClAkEAwOWQx3znWRNg6OPX
            Vy7qoOtY4eMwQUiEvy3tikLc7ePJQdKnejAcxTNgypdunhmoQT0jnojUuKq9dfcY
            hgLpbQJAMNfQwrBXLt73vwquhKVPqqWOl1SkJn2SUW6dQhH9d6iWxRpi2o54IFx1
            /JJIpbh/2Niy/ysew70xobd0hx0KDQJBAJ+JfnuvLzw5zc3wGvsNX5ql64Yvu4pS
            6w+fcbYHZOgPnDWnf0KMpk7nFmeYZTG3cLQ8V8hXzO/KrxXwvsSfheUCQQDPYO9U
            Xbc+7Ebq/uwt5d4b78pM7r/H1AErle1Mufxd79yE14KvQg+l6yqW0/IE3iwAcQv5
            5GaR2VvkhzdctbmZ
            -----END PRIVATE KEY-----
            """;

    @Test
    void unscopedServiceAccountCredentialsAreUpgradedToJwtAccessCredentials() throws Exception {
        ServiceAccountCredentials credentials = ServiceAccountCredentials.fromPkcs8(
                "test-client-id",
                "test-service-account@example.iam.gserviceaccount.com",
                TEST_PRIVATE_KEY,
                "test-private-key-id",
                Collections.emptyList());

        CallCredentials callCredentials = MoreCallCredentials.from(credentials);
        AtomicReference<Metadata> appliedHeaders = new AtomicReference<>();
        AtomicReference<Status> failure = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);

        callCredentials.applyRequestMetadata(
                new TestRequestInfo(),
                Runnable::run,
                new CallCredentials.MetadataApplier() {
                    @Override
                    public void apply(Metadata headers) {
                        appliedHeaders.set(headers);
                        completed.countDown();
                    }

                    @Override
                    public void fail(Status status) {
                        failure.set(status);
                        completed.countDown();
                    }
                });

        assertTrue(
                completed.await(10, TimeUnit.SECONDS),
                "credential metadata callback did not complete");
        assertNull(failure.get(), () -> "credentials failed with status: " + failure.get());
        Metadata headers = appliedHeaders.get();
        assertNotNull(headers);
        String authorization = headers.get(AUTHORIZATION_HEADER);
        assertNotNull(authorization);
        assertTrue(authorization.startsWith("Bearer "));
        assertEquals(3, authorization.substring("Bearer ".length()).split("\\.").length);
    }

    private static final class TestRequestInfo extends CallCredentials.RequestInfo {
        @Override
        public MethodDescriptor<?, ?> getMethodDescriptor() {
            return METHOD;
        }

        @Override
        public SecurityLevel getSecurityLevel() {
            return SecurityLevel.PRIVACY_AND_INTEGRITY;
        }

        @Override
        public String getAuthority() {
            return "example.test:443";
        }

        @Override
        public Attributes getTransportAttrs() {
            return Attributes.EMPTY;
        }

        @Override
        public CallOptions getCallOptions() {
            return CallOptions.DEFAULT;
        }
    }
}
