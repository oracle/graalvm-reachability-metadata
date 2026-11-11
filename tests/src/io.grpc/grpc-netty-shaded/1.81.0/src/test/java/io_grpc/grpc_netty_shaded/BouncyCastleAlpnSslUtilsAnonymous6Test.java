/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import javax.net.ssl.SSLEngine;
import org.junit.jupiter.api.Test;

public class BouncyCastleAlpnSslUtilsAnonymous6Test {
    @Test
    void retrievedSelectorHandlesAnUnsupportedOffer() throws Exception {
        SSLEngine engine = BouncyCastleEngineSupport.engine(true);
        try {
            assertEquals("", engine.getHandshakeApplicationProtocolSelector().apply(engine, List.of("other")));
        } finally {
            engine.closeOutbound();
        }
    }
}
