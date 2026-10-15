/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.BiFunction;
import javax.net.ssl.SSLEngine;
import org.apache.pulsar.shade.io.netty.buffer.UnpooledByteBufAllocator;
import org.apache.pulsar.shade.io.netty.handler.ssl.ApplicationProtocolConfig;
import org.apache.pulsar.shade.io.netty.handler.ssl.SslContext;
import org.apache.pulsar.shade.io.netty.handler.ssl.SslContextBuilder;
import org.apache.pulsar.shade.io.netty.handler.ssl.SslProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class JdkAlpnSslUtilsTest {
    @Test
    void configuresAndQueriesJdkAlpnEngine() throws Exception {
        ApplicationProtocolConfig protocols = new ApplicationProtocolConfig(
                ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                "h2",
                "http/1.1");
        SslContext context = SslContextBuilder.forClient()
                .sslProvider(SslProvider.JDK)
                .applicationProtocolConfig(protocols)
                .build();
        SSLEngine engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT);
        try {
            BiFunction<SSLEngine, List<String>, String> selector =
                    (ignored, offered) -> offered.contains("h2") ? "h2" : null;
            engine.setHandshakeApplicationProtocolSelector(selector);

            assertThat(engine.getSSLParameters().getApplicationProtocols())
                    .containsExactly("h2", "http/1.1");
            assertThat(engine.getHandshakeApplicationProtocolSelector()).isSameAs(selector);
            assertThat(engine.getApplicationProtocol()).isIn(null, "");
            assertThat(engine.getHandshakeApplicationProtocol()).isNull();
        } finally {
            engine.closeOutbound();
        }
    }
}
