/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import javax.net.ssl.SSLEngine;
import org.junit.jupiter.api.Test;

public class BouncyCastleAlpnSslUtilsAnonymous1Test {
    @Test
    void advertisesConfiguredProtocolsInPreferenceOrder() throws Exception {
        SSLEngine engine = BouncyCastleEngineSupport.engine(false);
        try {
            assertArrayEquals(new String[]{"h2", "http/1.1"}, engine.getSSLParameters().getApplicationProtocols());
        } finally {
            engine.closeOutbound();
        }
    }
}
