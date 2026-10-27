/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Provider;
import java.security.SecureRandom;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

public class Java9PlatformUtilTest {
    @Test
    void engineSslParametersRoundTripApplicationProtocols() throws Exception {
        Conscrypt.checkAvailability();
        Provider provider = Conscrypt.newProvider();
        SSLContext context = SSLContext.getInstance("TLS", provider);
        context.init(null, null, new SecureRandom());

        SSLEngine engine = context.createSSLEngine("localhost", 443);
        assertThat(Conscrypt.isConscrypt(engine)).isTrue();

        String[] protocols = {"h2", "http/1.1"};
        SSLParameters parameters = new SSLParameters();
        parameters.setApplicationProtocols(protocols);

        engine.setSSLParameters(parameters);
        assertThat(Conscrypt.getApplicationProtocols(engine)).containsExactly(protocols);

        SSLParameters copiedParameters = engine.getSSLParameters();
        assertThat(copiedParameters.getApplicationProtocols()).containsExactly(protocols);
    }
}
