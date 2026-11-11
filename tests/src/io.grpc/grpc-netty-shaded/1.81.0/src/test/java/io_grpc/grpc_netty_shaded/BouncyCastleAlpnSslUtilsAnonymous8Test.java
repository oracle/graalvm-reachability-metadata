/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import javax.net.ssl.SSLEngine;
import org.junit.jupiter.api.Test;

public class BouncyCastleAlpnSslUtilsAnonymous8Test {
    @Test
    void retrievedSelectorForwardsEngineAndOfferedProtocols() throws Exception {
        SSLEngine engine = BouncyCastleEngineSupport.engine(true);
        List<String> protocols = List.of("h2", "http/1.1");
        try {
            engine.setHandshakeApplicationProtocolSelector((connection, offered) -> {
                assertSame(engine, connection);
                assertEquals(protocols, offered);
                return offered.get(1);
            });
            assertEquals("http/1.1", engine.getHandshakeApplicationProtocolSelector().apply(engine, protocols));
        } finally {
            engine.closeOutbound();
        }
    }
}
