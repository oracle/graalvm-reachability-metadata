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
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.SecurityLevel;
import io.grpc.Status;
import io.grpc.auth.MoreCallCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class GoogleAuthLibraryCallCredentialsInnerJwtHelperTest {
    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of(
            "authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Test
    public void fromConvertsUnscopedServiceAccountCredentialsToJwtCredentials() {
        ServiceAccountCredentials serviceAccountCredentials = new ServiceAccountCredentials(
                "client-id", "client@example.test", null, "private-key-id", "quota-project-id",
                Collections.emptyList());

        CallCredentials callCredentials = MoreCallCredentials.from(serviceAccountCredentials);
        CapturingMetadataApplier applier = new CapturingMetadataApplier();

        callCredentials.applyRequestMetadata(new TestRequestInfo(), Runnable::run, applier);

        assertThat(applier.failure).isNull();
        assertThat(applier.metadata).isNotNull();
        assertThat(applier.metadata.get(AUTHORIZATION)).isEqualTo(
                "Bearer client-id:client@example.test:private-key-id:quota-project-id:"
                        + "https://example.test/coverage.Service");
    }

    private static final class TestRequestInfo extends CallCredentials.RequestInfo {
        @Override
        public MethodDescriptor<?, ?> getMethodDescriptor() {
            return MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(
                            "coverage.Service", "Method"))
                    .setRequestMarshaller(new ByteArrayMarshaller())
                    .setResponseMarshaller(new ByteArrayMarshaller())
                    .build();
        }

        @Override
        public SecurityLevel getSecurityLevel() {
            return SecurityLevel.PRIVACY_AND_INTEGRITY;
        }

        @Override
        public String getAuthority() {
            return "example.test";
        }

        @Override
        public Attributes getTransportAttrs() {
            return Attributes.EMPTY;
        }
    }

    private static final class CapturingMetadataApplier extends CallCredentials.MetadataApplier {
        private Metadata metadata;
        private Status failure;

        @Override
        public void apply(Metadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public void fail(Status status) {
            this.failure = status;
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
                throw new IllegalStateException("Failed to read marshalled bytes", e);
            }
        }
    }
}
