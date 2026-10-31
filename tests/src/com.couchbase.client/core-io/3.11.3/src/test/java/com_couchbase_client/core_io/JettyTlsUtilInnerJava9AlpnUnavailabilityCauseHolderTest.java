/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.io.grpc.netty.GrpcSslContexts;
import com.couchbase.client.core.deps.io.netty.buffer.UnpooledByteBufAllocator;
import com.couchbase.client.core.deps.io.netty.handler.ssl.SslContext;
import com.couchbase.client.core.deps.io.netty.handler.ssl.SslContextBuilder;
import com.couchbase.client.core.deps.io.netty.handler.ssl.SslProvider;
import javax.net.ssl.SSLEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class JettyTlsUtilInnerJava9AlpnUnavailabilityCauseHolderTest {
    @Test
    void exercisesPublicApi() throws Exception {
        SslContext context = GrpcSslContexts.configure(SslContextBuilder.forClient(), SslProvider.JDK).build();
        SSLEngine engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT);
        try {
            assertArrayEquals(new String[]{"h2"}, engine.getSSLParameters().getApplicationProtocols());
        } finally {
            engine.closeOutbound();
        }
    }
}
