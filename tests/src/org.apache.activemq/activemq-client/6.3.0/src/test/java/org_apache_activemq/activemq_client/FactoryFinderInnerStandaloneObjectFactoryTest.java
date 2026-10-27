/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.transport.TransportFactory;
import org.apache.activemq.transport.TransportServer;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryFinderInnerStandaloneObjectFactoryTest {

    @Test
    void bindsNioTransportWhenContextClassLoaderCannotResolveActiveMqServices() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        TransportServer server;
        try {
            thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
            server = TransportFactory.bind(new URI("nio://localhost:0"));
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }

        try {
            server.start();
            assertThat(server.getConnectURI().getScheme()).isEqualTo("nio");
            assertThat(server.getSocketAddress().getPort()).isPositive();
        } finally {
            server.stop();
        }
    }
}
