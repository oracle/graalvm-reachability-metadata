/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.OpenSslEngine;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLEngine;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenSslEngineTest {
    @Test
    void appliesSniAndCipherOrderThroughSslParametersWhenOpenSslIsAvailable() throws Exception {
        assertThat(OpenSsl.isAvailable()).isTrue();

        SslContext context = SslContextBuilder.forClient().sslProvider(SslProvider.OPENSSL).build();
        SSLEngine engine = context.newEngine(ByteBufAllocator.DEFAULT, "netty.io", 443);
        assertThat(engine).isInstanceOf(OpenSslEngine.class);

        try {
            SSLParameters parameters = new SSLParameters();
            parameters.setServerNames(Collections.singletonList(new SNIHostName("netty.io")));
            parameters.setUseCipherSuitesOrder(true);

            engine.setSSLParameters(parameters);
            SSLParameters copiedParameters = engine.getSSLParameters();

            assertThat(copiedParameters.getServerNames()).hasSize(1);
            assertThat(copiedParameters.getUseCipherSuitesOrder()).isTrue();
        } finally {
            ((OpenSslEngine) engine).shutdown();
        }
    }
}
