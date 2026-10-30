/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import org.junit.jupiter.api.Test;

import io.grpc.CallOptions;

public class X509AuthorityVerifierTest {
    @Test
    void verifiesAPerCallAuthorityAgainstThePeerCertificate() throws Exception {
        TlsTestSupport.grpcExchange("verified authority", CallOptions.DEFAULT.withAuthority("echo.local"));
    }
}
