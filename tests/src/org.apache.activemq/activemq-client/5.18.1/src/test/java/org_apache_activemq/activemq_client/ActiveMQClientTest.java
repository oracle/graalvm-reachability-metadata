/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQPrefetchPolicy;
import org.apache.activemq.RedeliveryPolicy;
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

    private static final String BROKER_URL_BASE = "tcp://localhost:61616";

    private static final String WIRE_FORMAT_BROKER_URL = BROKER_URL_BASE +
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

    private static final String JMS_BROKER_URL = BROKER_URL_BASE +
            "?jms.alwaysSessionAsync=true" +
            "&jms.alwaysSyncSend=false" +
            "&jms.auditDepth=2048" +
            "&jms.auditMaximumProducerNumber=64" +
            "&jms.checkForDuplicates=true" +
            "&jms.clientID=test-id" +
            "&jms.closeTimeout=15000" +
            "&jms.consumerExpiryCheckEnabled=true" +
            "&jms.copyMessageOnSend=true" +
            "&jms.disableTimeStampsByDefault=false" +
            "&jms.dispatchAsync=false" +
            "&jms.nestedMapAndListEnabled=true" +
            "&jms.objectMessageSerializationDefered=false" +
            "&jms.optimizeAcknowledge=false" +
            "&jms.optimizeAcknowledgeTimeOut=300" +
            "&jms.optimizedAckScheduledAckInterval=0" +
            "&jms.optimizedMessageDispatch=true" +
            "&jms.useAsyncSend=false" +
            "&jms.useCompression=false" +
            "&jms.useRetroactiveConsumer=false" +
            "&jms.warnAboutUnstartedConnectionTimeout=500" +
            "&jms.nonBlockingRedelivery=false" +
            "&jms.prefetchPolicy.queuePrefetch=" + ActiveMQPrefetchPolicy.DEFAULT_QUEUE_PREFETCH +
            "&jms.prefetchPolicy.queueBrowserPrefetch=" + ActiveMQPrefetchPolicy.DEFAULT_QUEUE_BROWSER_PREFETCH +
            "&jms.prefetchPolicy.topicPrefetch=" + ActiveMQPrefetchPolicy.DEFAULT_TOPIC_PREFETCH +
            "&jms.prefetchPolicy.durableTopicPrefetch=" + ActiveMQPrefetchPolicy.DEFAULT_DURABLE_TOPIC_PREFETCH +
            "&jms.prefetchPolicy.optimizeDurableTopicPrefetch=" + ActiveMQPrefetchPolicy.DEFAULT_OPTIMIZE_DURABLE_TOPIC_PREFETCH +
            "&jms.prefetchPolicy.maximumPendingMessageLimit=100" +
            "&jms.redeliveryPolicy.collisionAvoidancePercent=10" +
            "&jms.redeliveryPolicy.maximumRedeliveries=" + RedeliveryPolicy.DEFAULT_MAXIMUM_REDELIVERIES +
            "&jms.redeliveryPolicy.maximumRedeliveryDelay=-1" +
            "&jms.redeliveryPolicy.initialRedeliveryDelay=1000" +
            "&jms.redeliveryPolicy.useCollisionAvoidance=false" +
            "&jms.redeliveryPolicy.useExponentialBackOff=false" +
            "&jms.redeliveryPolicy.backOffMultiplier=5.0" +
            "&jms.redeliveryPolicy.redeliveryDelay=1000" +
            "&jms.redeliveryPolicy.preDispatchCheck=true" +
            "&jms.blobTransferPolicy.uploadUrl=http://localhost:8080/uploads/" +
            "&jms.blobTransferPolicy.brokerUploadUrl=http://localhost:8080/uploads/" +
            "&jms.blobTransferPolicy.bufferSize=" + 128 * 1024;

    private static final Logger logger = LoggerFactory.getLogger("ActiveMQClientTest");

    private BrokerService brokerService;

    @BeforeAll
    void beforeAll() throws Exception {
        logger.info("Starting embedded ActiveMQ broker ...");
        brokerService = new BrokerService();
        brokerService.addConnector(BROKER_URL_BASE);
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
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(BROKER_URL_BASE + "?wireFormat.version=" + wireFormatVersion);
            try (Connection connection = connectionFactory.createConnection()) {
                assertThat(connection).isNotNull();
            }
        }
        List<String> brokerUrls = Arrays.asList(WIRE_FORMAT_BROKER_URL, JMS_BROKER_URL);
        for (String brokerUrl : brokerUrls) {
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
            try (Connection connection = connectionFactory.createConnection()) {
                assertThat(connection).isNotNull();
            }
        }
    }

    private Connection createConnection() throws JMSException {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(BROKER_URL_BASE);
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
