/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.PropertiesBrokerFactory;
import org.junit.jupiter.api.Test;

public class PropertiesBrokerFactoryTest {

    @Test
    void configuresBrokerFromClasspathProperties() throws Exception {
        PropertiesBrokerFactory factory = new PropertiesBrokerFactory();

        BrokerService brokerService =
                factory.createBroker(new URI("properties:activemq-broker-test.properties"));

        assertThat(brokerService.getBrokerName()).isEqualTo("properties-broker");
        assertThat(brokerService.isPersistent()).isFalse();
        assertThat(brokerService.isUseJmx()).isFalse();
        assertThat(brokerService.isUseShutdownHook()).isFalse();
    }
}
