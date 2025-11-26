/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_broker;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.jupiter.api.Test;

import jakarta.jms.Connection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActivemqBrokerTest {

    private static final String BROKER_URL_BASE = "vm://" + UUID.randomUUID().toString().replaceAll("-", "") + "?broker.persistent=false";

    @Test
    void testEmbeddedBrokerConnection() throws Exception {
        BrokerService brokerService = new BrokerService();
        brokerService.addConnector(BROKER_URL_BASE);
        brokerService.setUseJmx(false);
        brokerService.getManagementContext().setCreateConnector(false);
        brokerService.setUseShutdownHook(false);
        brokerService.setPersistent(false);
        brokerService.setBrokerName("embedded-broker");
        brokerService.start();
        brokerService.waitUntilStarted();

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(BROKER_URL_BASE);
        try (Connection connection = connectionFactory.createConnection()) {
            assertThat(connection).isNotNull();
        }

        brokerService.stop();
        brokerService.waitUntilStopped();
    }
}
