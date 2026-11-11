/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.function.BiFunction;
import javax.net.ssl.SSLEngine;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.buffer.ByteBufAllocator;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;

public class JdkAlpnSslUtilsTest {
    @Test
    void negotiatesHttp2AndExchangesEncryptedMessages() throws Exception {
        TlsTestSupport.exchange(SslProvider.JDK, null);
    }

    @Test
    void exposesAndReplacesTheServerProtocolSelector() throws Exception {
        SslContext context = TlsTestSupport.context(true, SslProvider.JDK, null);
        SSLEngine engine = context.newEngine(ByteBufAllocator.DEFAULT);
        try {
            assertNull(engine.getHandshakeApplicationProtocol());
            BiFunction<SSLEngine, List<String>, String> selector = engine.getHandshakeApplicationProtocolSelector();
            assertNotNull(selector);
            assertEquals("h2", selector.apply(engine, List.of("h2")));
            engine.setHandshakeApplicationProtocolSelector((connection, offered) -> offered.get(0));
            assertEquals("http/1.1", engine.getHandshakeApplicationProtocolSelector()
                    .apply(engine, List.of("http/1.1")));
        } finally {
            engine.closeOutbound();
        }
    }
}
