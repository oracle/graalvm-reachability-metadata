/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.net.ssl.SSLEngine;

import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.buffer.ByteBufAllocator;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;

public class BouncyCastleAlpnSslUtilsAnonymous2Test {
    @Test
    void preservesClientProtocolPreferenceBeforeNegotiation() throws Exception {
        SslContext context = TlsTestSupport.context(false,
                new BouncyCastleJsseProvider(), SslProvider.JDK, "h2", "http/1.1");
        SSLEngine engine = context.newEngine(ByteBufAllocator.DEFAULT);
        assertArrayEquals(new String[] {"h2", "http/1.1"}, engine.getSSLParameters().getApplicationProtocols());
        assertNull(engine.getApplicationProtocol());
        engine.closeOutbound();
    }
}
