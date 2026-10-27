/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.PropertiesBrokerFactory;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertiesBrokerFactoryTest {

    @Test
    void createsBrokerFromClasspathProperties() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());

        try {
            BrokerService broker = new PropertiesBrokerFactory().createBroker(
                    URI.create("properties:activemq-broker.properties"));

            assertThat(broker.isPersistent()).isFalse();
            assertThat(broker.isUseJmx()).isFalse();
            assertThat(broker.getBrokerName()).isEqualTo("properties-broker");
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }
}
