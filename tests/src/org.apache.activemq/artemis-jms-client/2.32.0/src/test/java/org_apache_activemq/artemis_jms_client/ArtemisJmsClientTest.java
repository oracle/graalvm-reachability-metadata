/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_jms_client;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArtemisJmsClientTest {

    private static final String QUEUE_NAME = "queue-test-" + new Random().nextLong();

    private static final Logger logger = LoggerFactory.getLogger("ArtemisJmsClientTest");

    private ActiveMQServer server;

    private ActiveMQConnectionFactory connectionFactory;

    @BeforeAll
    void beforeAll() throws Exception {
        logger.info("Starting embedded Artemis broker ...");
        Configuration config = new ConfigurationImpl();
        config.addAcceptorConfiguration("tcp", "tcp://127.0.0.1:61616");
        config.setSecurityEnabled(false);

        server = new ActiveMQServerImpl(config);
        server.start();
        server.waitForActivation(1, TimeUnit.MINUTES);
        logger.info("Started embedded Artemis broker");

        connectionFactory = new ActiveMQConnectionFactory();
    }

    @AfterAll
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.close();
        }

        if (server != null) {
            logger.info("Stopping embedded Artemis broker ...");
            try {
                server.stop();
                logger.info("Stopped embedded Artemis broker");
            } catch (Exception ex) {
                logger.warn("Failed to stop embedded Artemis broker", ex);
            }
        }
    }

    @Test
    void testSendingAndReceivingMessage() throws Exception {
        String text = "hello";
        sendMessage(text);
        assertThat(receiveMessage()).isEqualTo(text);
    }

    private void sendMessage(String text) throws Exception {
        try (Connection connection = connectionFactory.createConnection()) {
            connection.start();
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                Destination destination = session.createQueue(QUEUE_NAME);
                try (MessageProducer producer = session.createProducer(destination)) {
                    producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
                    producer.send(session.createTextMessage(text));
                    logger.info("Sent text message: {}", text);
                }
            }
        }
    }

    private String receiveMessage() throws Exception {
        try (Connection connection = connectionFactory.createConnection()) {
            connection.start();
            connection.setExceptionListener(e -> logger.error("JMS Exception", e));
            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                Destination destination = session.createQueue(QUEUE_NAME);
                try (MessageConsumer consumer = session.createConsumer(destination)) {
                    Message message = consumer.receive(1000);
                    String text = ((TextMessage) message).getText();
                    logger.info("Received text message: {}", text);
                    return text;
                }
            }
        }
    }
}
