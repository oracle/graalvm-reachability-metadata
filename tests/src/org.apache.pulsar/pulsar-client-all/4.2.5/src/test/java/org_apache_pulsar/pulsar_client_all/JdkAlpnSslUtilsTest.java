/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import javax.net.ssl.SSLEngine;
import org.apache.pulsar.shade.io.netty.buffer.UnpooledByteBufAllocator;
import org.apache.pulsar.shade.io.netty.handler.ssl.ApplicationProtocolConfig;
import org.apache.pulsar.shade.io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import org.apache.pulsar.shade.io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import org.apache.pulsar.shade.io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import org.apache.pulsar.shade.io.netty.handler.ssl.SslContext;
import org.apache.pulsar.shade.io.netty.handler.ssl.SslContextBuilder;
import org.apache.pulsar.shade.io.netty.handler.ssl.SslProvider;
import org.junit.jupiter.api.Test;

public class JdkAlpnSslUtilsTest {

    @Test
    void configuresJdkEngineWithApplicationProtocols() throws Exception {
        final ApplicationProtocolConfig protocolConfig = new ApplicationProtocolConfig(
                Protocol.ALPN,
                SelectorFailureBehavior.NO_ADVERTISE,
                SelectedListenerFailureBehavior.ACCEPT,
                "h2",
                "http/1.1");
        final SslContext context = SslContextBuilder.forClient()
                .sslProvider(SslProvider.JDK)
                .applicationProtocolConfig(protocolConfig)
                .build();
        final SSLEngine engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT, "localhost", 443);
        try {
            assertThat(engine.getSSLParameters().getApplicationProtocols()).containsExactly("h2", "http/1.1");
            assertThat(engine.getApplicationProtocol()).isNull();
            assertThat(engine.getHandshakeApplicationProtocol()).isNull();
        } finally {
            engine.closeOutbound();
        }
    }
}
