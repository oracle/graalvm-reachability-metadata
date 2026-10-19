/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_jms_client;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSProducer;
import javax.jms.JMSRuntimeException;
import javax.jms.Queue;
import javax.jms.TextMessage;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Routes payment notifications through the broker-side message lifecycle that Artemis
 * `AddressSettings` drive for JMS clients: dead-lettering after `maxDeliveryAttempts`
 * rollbacks, expiry of time-to-live messages into an expiry address, the original-address
 * headers on rerouted messages, and duplicate detection through `_AMQ_DUPL_ID`.
 *
 * A payment gateway publishes notifications; a poison notification that the ledger keeps
 * rolling back lands in the dead-letter queue, a stale one whose time-to-live elapses lands
 * in the expiry queue, and a gateway retry carrying the same duplicate ID is discarded.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentNotificationDeadLetterTest {

    static {
        // Like the Artemis distribution, keep the broker audit log off: it is not the subject here.
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.activemq.audit", "warn");
    }

    private static final String NOTIFICATIONS = "payments.notifications";

    private static final String DEAD_LETTERS = "payments.dead";

    private static final String EXPIRED = "payments.expired";

    private static final int MAX_DELIVERY_ATTEMPTS = 3;

    private static final String DUPLICATE_ID = Message.HDR_DUPLICATE_DETECTION_ID.toString();

    private ActiveMQServer broker;

    private ActiveMQConnectionFactory connectionFactory;

    @BeforeAll
    void startBroker() throws Exception {
        String url = "tcp://127.0.0.1:" + freePort();
        AddressSettings paymentSettings = new AddressSettings()
                .setMaxDeliveryAttempts(MAX_DELIVERY_ATTEMPTS)
                .setRedeliveryDelay(0)
                .setDeadLetterAddress(SimpleString.toSimpleString(DEAD_LETTERS))
                .setExpiryAddress(SimpleString.toSimpleString(EXPIRED));
        Configuration configuration = new ConfigurationImpl()
                .setPersistenceEnabled(false)
                .setSecurityEnabled(false)
                .setMessageExpiryScanPeriod(100)
                .addAcceptorConfiguration("netty", url)
                .addAddressSetting("payments.notifications", paymentSettings)
                .addQueueConfiguration(anycastQueue(NOTIFICATIONS))
                .addQueueConfiguration(anycastQueue(DEAD_LETTERS))
                .addQueueConfiguration(anycastQueue(EXPIRED));
        broker = ActiveMQServers.newActiveMQServer(configuration);
        broker.start();
        assertThat(broker.waitForActivation(30, TimeUnit.SECONDS)).isTrue();
        connectionFactory = new ActiveMQConnectionFactory(url);
    }

    @AfterAll
    void stopBroker() throws Exception {
        if (connectionFactory != null) {
            connectionFactory.close();
        }
        if (broker != null) {
            broker.stop();
        }
    }

    @Test
    void reroutesPoisonAndStaleNotificationsAndDropsDuplicates() throws Exception {
        try (JMSContext gateway = connectionFactory.createContext()) {
            Queue notifications = gateway.createQueue(NOTIFICATIONS);
            JMSProducer producer = gateway.createProducer();
            producer.setProperty(DUPLICATE_ID, "PAY-1")
                    .send(notifications, "PAY-1 captured 120.00 EUR");
            producer.setProperty(DUPLICATE_ID, "PAY-1")
                    .send(notifications, "PAY-1 captured 120.00 EUR (gateway retry)");
            producer.setProperty(DUPLICATE_ID, "PAY-2")
                    .send(notifications, "PAY-2 captured 0.00 EUR");
        }
        assertThat(awaitMessageCount(NOTIFICATIONS, 2)).isEqualTo(2);

        try (JMSContext ledger = connectionFactory.createContext(JMSContext.SESSION_TRANSACTED)) {
            JMSConsumer consumer = ledger.createConsumer(ledger.createQueue(NOTIFICATIONS));

            TextMessage valid = (TextMessage) consumer.receive(10_000);
            assertThat(valid).isNotNull();
            assertThat(valid.getText()).isEqualTo("PAY-1 captured 120.00 EUR");
            assertThat(valid.getStringProperty(DUPLICATE_ID)).isEqualTo("PAY-1");
            ledger.commit();

            for (int attempt = 1; attempt <= MAX_DELIVERY_ATTEMPTS; attempt++) {
                TextMessage poison = (TextMessage) consumer.receive(10_000);
                assertThat(poison).isNotNull();
                assertThat(poison.getText()).isEqualTo("PAY-2 captured 0.00 EUR");
                assertThat(poison.getIntProperty("JMSXDeliveryCount")).isEqualTo(attempt);
                ledger.rollback();
            }
            assertThat(awaitMessageCount(NOTIFICATIONS, 0)).isZero();
            assertThat(consumer.receiveNoWait()).isNull();
        }

        try (JMSContext operator = connectionFactory.createContext();
                JMSConsumer consumer = operator.createConsumer(operator.createQueue(DEAD_LETTERS))) {
            TextMessage deadLetter = (TextMessage) consumer.receive(10_000);
            assertThat(deadLetter).isNotNull();
            assertThat(deadLetter.getText()).isEqualTo("PAY-2 captured 0.00 EUR");
            assertThat(deadLetter.getStringProperty(Message.HDR_ORIGINAL_ADDRESS.toString()))
                    .isEqualTo(NOTIFICATIONS);
            assertThat(deadLetter.getStringProperty(Message.HDR_ORIGINAL_QUEUE.toString()))
                    .isEqualTo(NOTIFICATIONS);
            assertThat(consumer.receiveNoWait()).isNull();
        }

        try (JMSContext gateway = connectionFactory.createContext()) {
            gateway.createProducer()
                    .setTimeToLive(50)
                    .setProperty(DUPLICATE_ID, "PAY-3")
                    .send(gateway.createQueue(NOTIFICATIONS), "PAY-3 authorization hold");
        }

        try (JMSContext operator = connectionFactory.createContext();
                JMSConsumer consumer = operator.createConsumer(operator.createQueue(EXPIRED))) {
            TextMessage expired = (TextMessage) consumer.receive(10_000);
            assertThat(expired).isNotNull();
            assertThat(expired.getText()).isEqualTo("PAY-3 authorization hold");
            assertThat(expired.getStringProperty(Message.HDR_ORIGINAL_ADDRESS.toString()))
                    .isEqualTo(NOTIFICATIONS);
            assertThat(expired.getLongProperty(Message.HDR_ACTUAL_EXPIRY_TIME.toString()))
                    .isPositive();
        }
        assertThat(awaitMessageCount(NOTIFICATIONS, 0)).isZero();

        try (JMSContext gateway = connectionFactory.createContext(JMSContext.SESSION_TRANSACTED)) {
            gateway.createProducer()
                    .setProperty(DUPLICATE_ID, "PAY-1")
                    .send(gateway.createQueue(NOTIFICATIONS), "PAY-1 captured 120.00 EUR (replayed batch)");
            assertThatThrownBy(gateway::commit)
                    .isInstanceOf(JMSRuntimeException.class)
                    .hasMessageContaining("Duplicate message detected");
        }
        assertThat(awaitMessageCount(NOTIFICATIONS, 0)).isZero();
    }

    private static QueueConfiguration anycastQueue(String name) {
        return new QueueConfiguration(name).setAddress(name).setRoutingType(RoutingType.ANYCAST);
    }

    private long awaitMessageCount(String queue, long expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        long count = broker.locateQueue(queue).getMessageCount();
        while (count != expected && System.nanoTime() < deadline) {
            Thread.sleep(50);
            count = broker.locateQueue(queue).getMessageCount();
        }
        return count;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
