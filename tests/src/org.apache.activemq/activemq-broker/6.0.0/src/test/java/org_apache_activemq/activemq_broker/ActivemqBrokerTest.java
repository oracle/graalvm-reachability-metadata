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
import org.junit.jupiter.api.Timeout;

import jakarta.jms.Connection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ActivemqBrokerTest {

    private static final String BROKER_NAME = UUID.randomUUID().toString().replaceAll("-", "");
    private static final String BROKER_URL_BASE = "vm://" + BROKER_NAME + "?create=false";

    @Test
    @Timeout(30)
    void testEmbeddedBrokerConnection() throws Exception {
        BrokerService brokerService = new BrokerService();
        brokerService.setUseJmx(false);
        brokerService.getManagementContext().setCreateConnector(false);
        brokerService.setUseShutdownHook(false);
        brokerService.setPersistent(false);
        brokerService.setBrokerName(BROKER_NAME);

        try {
            brokerService.start();
            brokerService.waitUntilStarted();

            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(BROKER_URL_BASE);
            try (Connection connection = connectionFactory.createConnection()) {
                assertThat(connection).isNotNull();
            }
        } finally {
            brokerService.stop();
            brokerService.waitUntilStopped();
        }
    }
}
