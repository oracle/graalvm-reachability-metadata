/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.openwire.OpenWireFormatFactory;
import org.apache.activemq.transport.fanout.FanoutTransportFactory;
import org.apache.activemq.util.FactoryFinder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryFinderInnerStandaloneObjectFactoryTest {

    @Test
    void discoversFactoryUsingTheContextClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(FactoryFinderInnerStandaloneObjectFactoryTest.class.getClassLoader());
            FactoryFinder finder = new FactoryFinder("META-INF/services/org/apache/activemq/transport/");

            Object factory = finder.newInstance("fanout");

            assertThat(factory).isInstanceOf(FanoutTransportFactory.class);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void discoversTheDefaultWireFormatFactoryFromItsServiceResource() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
            FactoryFinder finder = new FactoryFinder("META-INF/services/org/apache/activemq/wireformat/");

            Object factory = finder.newInstance("default");

            assertThat(factory).isInstanceOf(OpenWireFormatFactory.class);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }
}
