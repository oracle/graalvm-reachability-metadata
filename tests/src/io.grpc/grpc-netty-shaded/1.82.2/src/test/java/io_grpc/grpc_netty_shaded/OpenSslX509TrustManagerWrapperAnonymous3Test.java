/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;

public class OpenSslX509TrustManagerWrapperAnonymous3Test {
    @Test
    void validatesTheCertificateDuringANativeTlsHandshake() throws Exception {
        TlsTestSupport.exchange(false, SslProvider.OPENSSL, "http/1.1", "trusted native TLS payload");
    }
}
