/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.transport.TransportFactory;
import org.apache.activemq.transport.tcp.TcpTransportFactory;
import org.apache.activemq.util.FactoryFinder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryFinderTest {

    @Test
    void createsAnAllowedTransportFactoryWithTheLibraryClassLoaderFallback() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
            FactoryFinder<TransportFactory> finder = new FactoryFinder<>(
                    "META-INF/services/org/apache/activemq/transport/",
                    TransportFactory.class,
                    FactoryFinder.buildAllowedImpls(TcpTransportFactory.class));

            TransportFactory factory = finder.newInstance("tcp");

            assertThat(factory).isInstanceOf(TcpTransportFactory.class);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }
}
