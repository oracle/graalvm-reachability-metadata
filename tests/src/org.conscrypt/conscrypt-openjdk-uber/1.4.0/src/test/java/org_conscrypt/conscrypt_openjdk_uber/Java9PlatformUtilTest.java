/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.Provider;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

public class Java9PlatformUtilTest {
    @Test
    void sslEngineSslParametersRoundTripApplicationProtocols() throws Exception {
        Provider provider = Conscrypt.newProvider();
        SSLContext sslContext = SSLContext.getInstance("TLS", provider);
        sslContext.init(null, null, null);
        SSLEngine sslEngine = sslContext.createSSLEngine();
        assertTrue(Conscrypt.isConscrypt(sslEngine));

        String[] applicationProtocols = {"h2", "http/1.1"};
        SSLParameters configuredParameters = sslEngine.getSSLParameters();
        configuredParameters.setApplicationProtocols(applicationProtocols);

        sslEngine.setSSLParameters(configuredParameters);
        SSLParameters returnedParameters = sslEngine.getSSLParameters();

        assertArrayEquals(applicationProtocols, Conscrypt.getApplicationProtocols(sslEngine));
        assertArrayEquals(applicationProtocols, returnedParameters.getApplicationProtocols());
    }
}
