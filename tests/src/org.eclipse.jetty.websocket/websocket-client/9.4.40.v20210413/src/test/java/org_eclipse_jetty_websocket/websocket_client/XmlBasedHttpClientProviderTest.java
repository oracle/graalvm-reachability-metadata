/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_websocket.websocket_client;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.client.HttpClientProvider;
import org.junit.jupiter.api.Test;

public class XmlBasedHttpClientProviderTest {

    @Test
    void providerChecksForXmlConfigurationResource() throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        ClassLoader lookupClassLoader = XmlBasedHttpClientProviderTest.class.getClassLoader();
        assertThat(lookupClassLoader).isNotNull();

        HttpClient client = null;
        currentThread.setContextClassLoader(lookupClassLoader);
        try {
            client = HttpClientProvider.get(null);

            assertThat(client).isNotNull();
            assertThat(currentThread.getContextClassLoader()).isSameAs(lookupClassLoader);
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
            if (client != null) {
                client.stop();
            }
        }
    }
}
