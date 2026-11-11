/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.buffer.ByteBufAllocator;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import io.grpc.netty.shaded.io.netty.util.ReferenceCountUtil;

public class LibraryTest {
    @Test
    void initializedNativeTlsLibraryProducesAClientHello() throws Exception {
        SslContext context = TlsTestSupport.context(false, SslProvider.OPENSSL, null);
        SSLEngine engine = null;
        try {
            engine = context.newEngine(ByteBufAllocator.DEFAULT, "localhost", 443);
            ByteBuffer packet = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
            engine.beginHandshake();
            SSLEngineResult result = engine.wrap(ByteBuffer.allocate(0), packet);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertTrue(result.bytesProduced() > 5);
            assertEquals(22, packet.get(0));
        } finally {
            if (engine != null) {
                engine.closeOutbound();
                ReferenceCountUtil.release(engine);
            }
            ReferenceCountUtil.release(context);
        }
    }
}
