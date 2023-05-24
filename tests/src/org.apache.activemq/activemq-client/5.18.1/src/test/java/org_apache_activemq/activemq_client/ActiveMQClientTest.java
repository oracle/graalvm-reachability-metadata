/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.openwire.OpenWireFormat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ActiveMQClientTest {

    private static final String QUEUE_NAME = "queue-test-" + new Random().nextLong();

    private static final String BROKER_HOST = "tcp://localhost:61616";

    private static final String BROKER_URL = BROKER_HOST +
            "?wireFormat.cacheEnabled=true" +
            "&wireFormat.cacheSize=1024" +
            "&wireFormat.maxInactivityDuration=30000" +
            "&wireFormat.maxInactivityDurationInitalDelay=10000" +
            "&wireFormat.maxFrameSize=" + OpenWireFormat.DEFAULT_MAX_FRAME_SIZE +
            "&wireFormat.maxFrameSizeEnabled=true" +
            "&wireFormat.sizePrefixDisabled=false" +
            "&wireFormat.stackTraceEnabled=true" +
            "&wireFormat.tcpNoDelayEnabled=true" +
            "&wireFormat.tightEncodingEnabled=true";

    private static final Logger logger = LoggerFactory.getLogger("ActiveMQClientTest");

    private BrokerService brokerService;

    @BeforeAll
    void beforeAll() throws Exception {
        logger.info("Starting embedded ActiveMQ broker ...");
        brokerService = new BrokerService();
        brokerService.addConnector(BROKER_HOST);
        brokerService.setUseJmx(false);
        brokerService.getManagementContext().setCreateConnector(false);
        brokerService.setUseShutdownHook(false);
        brokerService.setPersistent(false);
        brokerService.setBrokerName("embedded-broker");
        brokerService.start();
        brokerService.waitUntilStarted();
        logger.info("Started embedded ActiveMQ broker");
    }

    @AfterAll
    void tearDown() {
        if (brokerService != null) {
            logger.info("Stopping embedded ActiveMQ broker ...");
            if (!brokerService.isStopped()) {
                try {
                    brokerService.stop();
                } catch (Exception ex) {
                    logger.warn("Failed to stop embedded ActiveMQ broker", ex);
                }
            }
            brokerService.waitUntilStopped();
            logger.info("Stopped embedded ActiveMQ broker");
        }
    }

    @Test
    void testSendingAndReceivingMessage() throws Exception {
        String text = "hello";
        sendMessage(text);
        assertThat(receiveMessage()).isEqualTo(text);
    }

    @Test
    void testConnections() throws Exception {
        List<Integer> wireFormatVersions = Arrays.asList(1, 9, 10, 11, 12);
        for (Integer wireFormatVersion : wireFormatVersions) {
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(BROKER_HOST + "?wireFormat.version=" + wireFormatVersion);
            try (Connection connection = connectionFactory.createConnection()) {
                assertThat(connection).isNotNull();
            }
        }
    }

    private Connection createConnection() throws JMSException {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(BROKER_URL);
        return connectionFactory.createConnection();
    }

    private void sendMessage(String text) throws Exception {
        try (Connection connection = createConnection()) {
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
        try (Connection connection = createConnection()) {
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
