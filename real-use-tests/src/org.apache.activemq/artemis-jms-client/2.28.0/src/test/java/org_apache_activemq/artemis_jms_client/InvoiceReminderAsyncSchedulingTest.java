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
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.jms.CompletionListener;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers JMS 2.0 asynchronous sends acknowledged through a `CompletionListener` together with
 * delayed delivery scheduled by `setDeliveryDelay` and the Artemis `_AMQ_SCHED_DELIVERY` property.
 *
 * A billing service publishes invoices without blocking on the broker, enabling send
 * acknowledgements with the `confirmationWindowSize` connector URL parameter, and learns each
 * invoice is stored when its completion callback fires in send order. Payment reminders are
 * scheduled for later: a far-future reminder stays invisible to consumers and browsers while the
 * broker counts it as scheduled, and a short-delay reminder is delivered no earlier than its
 * `JMSDeliveryTime`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceReminderAsyncSchedulingTest {

    static {
        // Like the Artemis distribution, keep the broker audit log off: it is not the subject here.
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.activemq.audit", "warn");
    }

    private static final String INVOICES = "billing.invoices";

    private static final String REMINDERS = "billing.reminders";

    private static final String SCHEDULED_DELIVERY = "_AMQ_SCHED_DELIVERY";

    private ActiveMQServer broker;

    private ActiveMQConnectionFactory connectionFactory;

    @BeforeAll
    void startBroker() throws Exception {
        String url = "tcp://127.0.0.1:" + freePort();
        Configuration configuration = new ConfigurationImpl()
                .setPersistenceEnabled(false)
                .setSecurityEnabled(false)
                .addAcceptorConfiguration("netty", url);
        broker = ActiveMQServers.newActiveMQServer(configuration);
        broker.start();
        assertThat(broker.waitForActivation(30, TimeUnit.SECONDS)).isTrue();
        connectionFactory = new ActiveMQConnectionFactory(url + "?confirmationWindowSize=1048576");
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
    void publishesInvoicesAsynchronouslyThroughJmsContext() throws Exception {
        assertThat(connectionFactory.getConfirmationWindowSize()).isEqualTo(1048576);
        List<String> invoiceIds = List.of("INV-1001", "INV-1002", "INV-1003", "INV-1004");
        Receipts receipts = new Receipts(invoiceIds.size());

        try (JMSContext billing = connectionFactory.createContext()) {
            Queue invoices = billing.createQueue(INVOICES);
            for (String invoiceId : invoiceIds) {
                billing.createProducer()
                        .setDeliveryMode(DeliveryMode.PERSISTENT)
                        .setAsync(receipts)
                        .setProperty("invoiceId", invoiceId)
                        .send(invoices, "amount-due:" + invoiceId);
            }
            assertThat(receipts.await()).isTrue();
        }

        assertThat(receipts.failures).isEmpty();
        assertThat(receipts.confirmed).containsExactlyElementsOf(invoiceIds);
        assertThat(receipts.messageIds).hasSize(invoiceIds.size()).allSatisfy(id -> assertThat(id).startsWith("ID:"));

        try (JMSContext ledger = connectionFactory.createContext()) {
            JMSConsumer consumer = ledger.createConsumer(ledger.createQueue(INVOICES));
            List<String> received = new ArrayList<>();
            List<String> receivedIds = new ArrayList<>();
            for (int i = 0; i < invoiceIds.size(); i++) {
                Message message = consumer.receive(10_000);
                assertThat(message).isNotNull();
                received.add(message.getBody(String.class));
                receivedIds.add(message.getJMSMessageID());
            }
            assertThat(received).containsExactly(
                    "amount-due:INV-1001", "amount-due:INV-1002", "amount-due:INV-1003", "amount-due:INV-1004");
            assertThat(receivedIds).containsExactlyElementsOf(receipts.messageIds);
            assertThat(consumer.receiveNoWait()).isNull();
        }
    }

    @Test
    void schedulesRemindersThroughClassicProducer() throws Exception {
        Connection connection = connectionFactory.createConnection();
        try {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue reminders = session.createQueue(REMINDERS);
            MessageProducer producer = session.createProducer(reminders);
            MessageConsumer consumer = session.createConsumer(reminders);

            producer.setDeliveryDelay(TimeUnit.HOURS.toMillis(1));
            assertThat(producer.getDeliveryDelay()).isEqualTo(TimeUnit.HOURS.toMillis(1));
            Receipts farReceipt = new Receipts(1);
            TextMessage farReminder = session.createTextMessage("final-notice:INV-2001");
            farReminder.setStringProperty("invoiceId", "INV-2001");
            long beforeFarSend = System.currentTimeMillis();
            producer.send(farReminder, farReceipt);
            assertThat(farReceipt.await()).isTrue();
            assertThat(farReceipt.failures).isEmpty();
            assertThat(farReceipt.confirmed).containsExactly("INV-2001");
            assertThat(farReminder.getJMSDeliveryTime())
                    .isGreaterThanOrEqualTo(beforeFarSend + TimeUnit.HOURS.toMillis(1));

            TextMessage pinnedReminder = session.createTextMessage("courtesy-notice:INV-2002");
            pinnedReminder.setStringProperty("invoiceId", "INV-2002");
            pinnedReminder.setLongProperty(SCHEDULED_DELIVERY, System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1));
            producer.setDeliveryDelay(0);
            producer.send(pinnedReminder);

            assertThat(awaitScheduledCount(REMINDERS, 2)).isEqualTo(2);
            assertThat(consumer.receiveNoWait()).isNull();
            try (QueueBrowser browser = session.createBrowser(reminders)) {
                Enumeration<?> pending = browser.getEnumeration();
                assertThat(pending.hasMoreElements()).isFalse();
            }

            producer.setDeliveryDelay(500);
            TextMessage soonReminder = session.createTextMessage("friendly-notice:INV-2003");
            soonReminder.setStringProperty("invoiceId", "INV-2003");
            producer.send(soonReminder);
            long scheduledFor = soonReminder.getJMSDeliveryTime();
            assertThat(scheduledFor).isGreaterThan(soonReminder.getJMSTimestamp());

            Message delivered = consumer.receive(10_000);
            long receivedAt = System.currentTimeMillis();
            assertThat(delivered).isNotNull();
            assertThat(((TextMessage) delivered).getText()).isEqualTo("friendly-notice:INV-2003");
            assertThat(delivered.getStringProperty("invoiceId")).isEqualTo("INV-2003");
            assertThat(delivered.getJMSDeliveryTime()).isEqualTo(scheduledFor);
            assertThat(receivedAt).isGreaterThanOrEqualTo(scheduledFor);
            assertThat(consumer.receiveNoWait()).isNull();
            assertThat(broker.locateQueue(REMINDERS).getScheduledCount()).isEqualTo(2);
        } finally {
            connection.close();
        }
    }

    private long awaitScheduledCount(String queue, long expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        long scheduled = broker.locateQueue(queue).getScheduledCount();
        while (scheduled != expected && System.nanoTime() < deadline) {
            Thread.sleep(50);
            scheduled = broker.locateQueue(queue).getScheduledCount();
        }
        return scheduled;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** Collects the broker's send acknowledgements the way a billing service tracks them. */
    static final class Receipts implements CompletionListener {

        final List<String> confirmed = new CopyOnWriteArrayList<>();

        final List<String> messageIds = new CopyOnWriteArrayList<>();

        final List<Exception> failures = new CopyOnWriteArrayList<>();

        private final CountDownLatch pending;

        Receipts(int expected) {
            pending = new CountDownLatch(expected);
        }

        @Override
        public void onCompletion(Message message) {
            try {
                confirmed.add(message.getStringProperty("invoiceId"));
                messageIds.add(message.getJMSMessageID());
            } catch (Exception e) {
                failures.add(e);
            } finally {
                pending.countDown();
            }
        }

        @Override
        public void onException(Message message, Exception exception) {
            failures.add(exception);
            pending.countDown();
        }

        boolean await() throws InterruptedException {
            return pending.await(10, TimeUnit.SECONDS);
        }
    }
}
