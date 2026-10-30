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

import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.buffer.ByteBufAllocator;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;

public class BouncyCastleAlpnSslUtilsAnonymous6Test {
    @Test
    void selectsTheMutuallySupportedFallbackProtocol() throws Exception {
        SslContext context = TlsTestSupport.context(true,
                new BouncyCastleJsseProvider(), SslProvider.JDK, "h2", "http/1.1");
        SSLEngine engine = context.newEngine(ByteBufAllocator.DEFAULT);
        assertEquals("http/1.1", engine.getHandshakeApplicationProtocolSelector().apply(engine, List.of("http/1.1")));
        engine.closeOutbound();
    }
}
