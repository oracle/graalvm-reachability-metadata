/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_oci_sdk.oci_java_sdk_common;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracle.bmc.Service;
import com.oracle.bmc.Services;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.circuitbreaker.CircuitBreakerConfiguration;
import com.oracle.bmc.common.ClientBuilderBase;
import com.oracle.bmc.http.client.HttpClientBuilder;
import com.oracle.bmc.http.client.HttpProvider;
import com.oracle.bmc.http.client.Serializer;
import com.oracle.bmc.http.internal.BaseSyncClient;
import com.oracle.bmc.http.signing.RequestSignerFactory;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class BaseClientTest {
    private static final String CLIENT_CODEGEN_VERSION = "3.0.0";
    private static final String MINIMUM_CLIENT_CODEGEN_VERSION = "1.0.0";

    @Test
    void constructorLoadsClientPropertiesFromClientPackageResource() {
        TestClientBuilder builder = new TestClientBuilder();
        RequestSignerFactory requestSignerFactory =
                (service, authProvider) ->
                        (uri, httpMethod, headers, body) -> Collections.emptyMap();
        builder.requestSignerFactory(requestSignerFactory);
        builder.httpProvider(new NoOpHttpProvider());

        try (TestClient client = builder.build(new TestAuthenticationDetailsProvider())) {
            assertThat(client.getClientCommonLibraryVersion()).isEqualTo(CLIENT_CODEGEN_VERSION);
            assertThat(client.getMinimumClientCommonLibraryVersionFromClient())
                    .contains(MINIMUM_CLIENT_CODEGEN_VERSION);
        }
    }

    private static final class NoOpHttpProvider implements HttpProvider {
        @Override
        public HttpClientBuilder newBuilder() {
            throw new AssertionError("Endpoint initialization is not part of this test");
        }

        @Override
        public Serializer getSerializer() {
            throw new AssertionError("Serialization is not part of this test");
        }
    }

    private static final class TestClientBuilder
            extends ClientBuilderBase<TestClientBuilder, TestClient> {
        private static final Service TEST_SERVICE =
                Services.serviceBuilder()
                        .serviceName("GRAALVM_REACHABILITY_METADATA_TEST_BASE_CLIENT")
                        .serviceEndpointPrefix("baseclienttest")
                        .serviceEndpointTemplate("https://baseclienttest.{region}.example.com")
                        .build();

        private TestClientBuilder() {
            super(TEST_SERVICE);
        }

        @Override
        public TestClient build(
                AbstractAuthenticationDetailsProvider authenticationDetailsProvider) {
            return new TestClient(this, authenticationDetailsProvider, null);
        }
    }

    private static final class TestClient extends BaseSyncClient {
        private TestClient(
                ClientBuilderBase<?, ?> builder,
                AbstractAuthenticationDetailsProvider authenticationDetailsProvider,
                CircuitBreakerConfiguration defaultCircuitBreaker) {
            super(builder, authenticationDetailsProvider, defaultCircuitBreaker);
        }
    }

    private static final class TestAuthenticationDetailsProvider
            implements AbstractAuthenticationDetailsProvider {}
}
