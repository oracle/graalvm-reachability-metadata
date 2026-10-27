/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_jms_client;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.management.MBeanServerFactory;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQObjectMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ActiveMQObjectMessageTest {

    private static final String QUEUE_NAME = "object-message-round-trip";
    private static final String EXPECTED_BODY = "serialized Artemis message body";

    private ActiveMQServer server;
    private ActiveMQConnectionFactory connectionFactory;

    @BeforeAll
    void startBroker() throws Exception {
        Configuration configuration = new ConfigurationImpl();
        configuration.addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getName()));
        configuration.setSecurityEnabled(false);
        configuration.setJMXManagementEnabled(false);
        configuration.setPersistenceEnabled(false);

        server = new ActiveMQServerImpl(configuration, MBeanServerFactory.newMBeanServer());
        server.start();
        assertThat(server.waitForActivation(30, TimeUnit.SECONDS)).isTrue();

        TransportConfiguration connector = new TransportConfiguration(InVMConnectorFactory.class.getName());
        connectionFactory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, connector);
    }

    @AfterAll
    void stopBroker() throws Exception {
        try {
            if (connectionFactory != null) {
                connectionFactory.close();
            }
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    @Test
    void sendsAndReceivesSerializableObjectBody() throws Exception {
        try (Connection connection = connectionFactory.createConnection();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            Destination destination = session.createQueue(QUEUE_NAME);
            try (MessageConsumer consumer = session.createConsumer(destination);
                    MessageProducer producer = session.createProducer(destination)) {
                producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
                ObjectMessage outgoing = session.createObjectMessage(EXPECTED_BODY);

                producer.send(outgoing);
                connection.start();

                Message received = consumer.receive(10_000);
                assertThat(received).isInstanceOf(ActiveMQObjectMessage.class);
                Serializable body = ((ObjectMessage) received).getObject();
                assertThat(body).isEqualTo(EXPECTED_BODY);
            }
        }
    }
}
