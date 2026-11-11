/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.net.ssl.SSLEngine;
import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.netty.buffer.ByteBufAllocator;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;

public class JettyTlsUtilInnerJava9AlpnUnavailabilityCauseHolderTest {
    @Test
    void configuresTheJdkClientForGrpcHttp2Negotiation() throws Exception {
        SslContext context = GrpcSslContexts.configure(SslContextBuilder.forClient(), SslProvider.JDK).build();
        SSLEngine engine = context.newEngine(ByteBufAllocator.DEFAULT, "localhost", 443);
        try {
            assertTrue(engine.getUseClientMode());
            assertArrayEquals(new String[]{"h2"}, engine.getSSLParameters().getApplicationProtocols());
        } finally {
            engine.closeOutbound();
        }
    }
}
