/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_oci_sdk.oci_java_sdk_common;

import com.oracle.bmc.Service;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.http.signing.RequestSigner;
import com.oracle.bmc.http.signing.RequestSignerFactory;
import com.oracle.bmc.identity.IdentityClient;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseClientTest {
    @Test
    void loadsGeneratedClientCompatibilityProperties() {
        try (IdentityClient client =
                IdentityClient.builder()
                        .requestSignerFactory(new NoOpRequestSignerFactory())
                        .build(new TestAuthenticationDetailsProvider())) {
            assertThat(client.getClientCommonLibraryVersion()).isNotBlank();
        }
    }

    private static final class TestAuthenticationDetailsProvider
            implements AbstractAuthenticationDetailsProvider {}

    private static final class NoOpRequestSignerFactory implements RequestSignerFactory {
        @Override
        public RequestSigner createRequestSigner(
                Service service, AbstractAuthenticationDetailsProvider authenticationDetailsProvider) {
            return new NoOpRequestSigner();
        }
    }

    private static final class NoOpRequestSigner implements RequestSigner {
        @Override
        public Map<String, String> signRequest(
                URI uri, String httpMethod, Map<String, List<String>> headers, Object body) {
            return Collections.emptyMap();
        }
    }
}
