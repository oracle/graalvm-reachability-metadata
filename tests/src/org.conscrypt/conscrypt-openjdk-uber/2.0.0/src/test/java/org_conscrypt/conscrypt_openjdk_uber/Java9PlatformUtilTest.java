/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import java.security.SecureRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Java9PlatformUtilTest {

    private static final String[] APPLICATION_PROTOCOLS = {"h2", "http/1.1"};

    @Test
    void roundTripsApplicationProtocolsThroughSslParametersOnConscryptEngine() throws Exception {
        SSLEngine engine = createSslContext().createSSLEngine("localhost", 443);
        engine.setUseClientMode(true);
        assertThat(Conscrypt.isConscrypt(engine)).isTrue();

        SSLParameters sslParameters = engine.getSSLParameters();
        sslParameters.setApplicationProtocols(APPLICATION_PROTOCOLS);

        engine.setSSLParameters(sslParameters);

        assertThat(Conscrypt.getApplicationProtocols(engine)).containsExactly(APPLICATION_PROTOCOLS);
        assertThat(engine.getSSLParameters().getApplicationProtocols()).containsExactly(APPLICATION_PROTOCOLS);
    }

    @Test
    void roundTripsApplicationProtocolsThroughSslParametersOnConscryptSocket() throws Exception {
        SSLSocketFactory socketFactory = createSslContext().getSocketFactory();
        try (SSLSocket socket = (SSLSocket) socketFactory.createSocket()) {
            assertThat(Conscrypt.isConscrypt(socket)).isTrue();

            SSLParameters sslParameters = socket.getSSLParameters();
            sslParameters.setApplicationProtocols(APPLICATION_PROTOCOLS);

            socket.setSSLParameters(sslParameters);

            assertThat(Conscrypt.getApplicationProtocols(socket)).containsExactly(APPLICATION_PROTOCOLS);
            assertThat(socket.getSSLParameters().getApplicationProtocols()).containsExactly(APPLICATION_PROTOCOLS);
        }
    }

    private static SSLContext createSslContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS", Conscrypt.newProvider());
        sslContext.init(null, null, new SecureRandom());
        return sslContext;
    }
}
