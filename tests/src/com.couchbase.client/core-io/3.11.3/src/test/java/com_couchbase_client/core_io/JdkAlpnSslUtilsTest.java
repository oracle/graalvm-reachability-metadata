/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.io.netty.buffer.UnpooledByteBufAllocator;
import com.couchbase.client.core.deps.io.netty.handler.ssl.ApplicationProtocolConfig;
import com.couchbase.client.core.deps.io.netty.handler.ssl.SslContext;
import com.couchbase.client.core.deps.io.netty.handler.ssl.SslContextBuilder;
import com.couchbase.client.core.deps.io.netty.handler.ssl.SslProvider;
import javax.net.ssl.SSLEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JdkAlpnSslUtilsTest {
    @Test
    void exercisesPublicApi() throws Exception {
        SslContext context = SslContextBuilder.forClient().sslProvider(SslProvider.JDK)
                .applicationProtocolConfig(new ApplicationProtocolConfig(ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT, "h2", "http/1.1"))
                .build();
        SSLEngine engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT, "localhost", 443);
        try {
            assertTrue(engine.getUseClientMode());
            assertArrayEquals(new String[]{"h2", "http/1.1"}, engine.getSSLParameters().getApplicationProtocols());
            assertNull(engine.getHandshakeApplicationProtocol());
        } finally {
            engine.closeOutbound();
        }
    }
}
