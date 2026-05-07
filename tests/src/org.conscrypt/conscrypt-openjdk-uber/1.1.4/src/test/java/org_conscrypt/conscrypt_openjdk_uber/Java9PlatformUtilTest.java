/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Provider;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

public class Java9PlatformUtilTest {
    private static final String[] APPLICATION_PROTOCOLS = {"h2", "http/1.1"};

    @Test
    void engineSslParametersRoundTripApplicationProtocolsThroughJava9PlatformUtil()
            throws Exception {
        SSLEngine engine = newConscryptEngine();
        SSLParameters parameters = engine.getSSLParameters();
        parameters.setApplicationProtocols(APPLICATION_PROTOCOLS);

        engine.setSSLParameters(parameters);

        assertThat(Conscrypt.getApplicationProtocols(engine)).containsExactly(APPLICATION_PROTOCOLS);
        assertThat(engine.getSSLParameters().getApplicationProtocols())
                .containsExactly(APPLICATION_PROTOCOLS);
    }

    private static SSLEngine newConscryptEngine() throws Exception {
        Provider provider = Conscrypt.newProvider();
        SSLContext context = SSLContext.getInstance("TLS", provider);
        context.init(null, null, null);
        return context.createSSLEngine("localhost", 443);
    }
}
