/*
 * Copyright and related rights waived via CC0
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.buffer.UnpooledByteBufAllocator;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.handler.ssl.ApplicationProtocolConfig;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import javax.net.ssl.SSLEngine;
import org.junit.jupiter.api.Test;

public class JdkAlpnSslUtilsTest {
    @Test
    void configuresJdkTlsEnginesForHttp2Alpn() throws Exception {
        ApplicationProtocolConfig protocols = new ApplicationProtocolConfig(ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT, "h2");
        SslContext context = SslContextBuilder.forClient().sslProvider(SslProvider.JDK)
                .applicationProtocolConfig(protocols).build();
        SSLEngine engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT, "localhost", 443);
        try {
            assertTrue(engine.getUseClientMode());
            assertArrayEquals(new String[] {"h2"}, engine.getSSLParameters().getApplicationProtocols());
        } finally {
            engine.closeOutbound();
        }
    }
}
