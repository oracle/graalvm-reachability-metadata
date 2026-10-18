/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.command.ActiveMQQueue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps per-account event order through message groups and exclusive consumers:
 * `JMSXGroupID`-sticky dispatch across competing listeners, group sequence numbers, the
 * broker's first-for-consumer marker, consumer prefetch, and exclusive-consumer failover
 * configured through destination options.
 *
 * A banking core emits interleaved events for several accounts onto one queue; two
 * competing balance workers must each see every event of an account in order, so the
 * events are grouped by account. Settlement batches, in contrast, must be handled by a
 * single worker at a time: a primary exclusive consumer takes them all until it shuts
 * down, and the standby then takes over.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountEventMessageGroupsTest {

    private static final String ACCOUNT_EVENTS = "accounts.events";

    private static final String SETTLEMENTS = "accounts.settlements";

    private static final List<String> ACCOUNTS = List.of("ACC-1", "ACC-2", "ACC-3");

    private static final int EVENTS_PER_ACCOUNT = 4;

    private static final long RECEIVE_TIMEOUT_MILLIS = 10_000L;

    private BrokerService broker;

    private ActiveMQConnectionFactory factory;

    @BeforeAll
    void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("accounts-broker");
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setUseShutdownHook(false);
        TransportConnector connector = broker.addConnector("tcp://127.0.0.1:0");
        broker.start();
        assertThat(broker.waitUntilStarted()).isTrue();
        factory = new ActiveMQConnectionFactory(connector.getConnectUri().toString());
        factory.getPrefetchPolicy().setQueuePrefetch(1);
    }

    @AfterAll
    void stopBroker() throws Exception {
        broker.stop();
        broker.waitUntilStopped();
    }

    @Test
    void routesEveryEventOfAnAccountToOneWorkerInOrder() throws Exception {
        Connection connection = factory.createConnection();
        try {
            CountDownLatch handled = new CountDownLatch(ACCOUNTS.size() * EVENTS_PER_ACCOUNT);
            BalanceWorker east = new BalanceWorker("east", handled);
            BalanceWorker west = new BalanceWorker("west", handled);
            east.listen(connection);
            west.listen(connection);
            connection.start();

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(session.createQueue(ACCOUNT_EVENTS));
            for (int sequence = 1; sequence <= EVENTS_PER_ACCOUNT; sequence++) {
                for (String account : ACCOUNTS) {
                    TextMessage event = session.createTextMessage(account + ":credit:" + sequence);
                    event.setStringProperty("JMSXGroupID", account);
                    event.setIntProperty("JMSXGroupSeq", sequence);
                    producer.send(event);
                }
            }

            assertThat(handled.await(RECEIVE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();

            List<Delivery> deliveries = new ArrayList<>(east.deliveries());
            deliveries.addAll(west.deliveries());
            assertThat(deliveries).hasSize(ACCOUNTS.size() * EVENTS_PER_ACCOUNT);

            Map<String, Set<String>> workersByAccount = deliveries.stream().collect(
                    Collectors.groupingBy(Delivery::account, TreeMap::new,
                            Collectors.mapping(Delivery::worker, Collectors.toSet())));
            assertThat(workersByAccount).containsOnlyKeys(ACCOUNTS);
            assertThat(workersByAccount.values()).allSatisfy(workers -> assertThat(workers).hasSize(1));

            for (BalanceWorker worker : List.of(east, west)) {
                Map<String, List<Delivery>> byAccount = worker.deliveries().stream().collect(
                        Collectors.groupingBy(Delivery::account));
                byAccount.forEach((account, events) -> {
                    assertThat(events).extracting(Delivery::sequence)
                            .as("%s order on %s", account, worker.name())
                            .containsExactly(1, 2, 3, 4);
                    assertThat(events).extracting(Delivery::body)
                            .containsExactly(account + ":credit:1", account + ":credit:2",
                                    account + ":credit:3", account + ":credit:4");
                    assertThat(events).extracting(Delivery::firstForWorker)
                            .containsExactly(true, false, false, false);
                });
            }
        } finally {
            connection.close();
        }
    }

    @Test
    void handsSettlementsToTheStandbyOnlyAfterThePrimaryExclusiveConsumerCloses()
            throws Exception {
        Connection connection = factory.createConnection();
        try {
            connection.start();
            Session primarySession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Session standbySession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue exclusive = primarySession.createQueue(SETTLEMENTS + "?consumer.exclusive=true");
            assertThat(exclusive.getQueueName()).isEqualTo(SETTLEMENTS);
            assertThat(((ActiveMQQueue) exclusive).getOptions())
                    .containsEntry("consumer.exclusive", "true");

            MessageConsumer primary = primarySession.createConsumer(exclusive);
            MessageConsumer standby = standbySession.createConsumer(
                    standbySession.createQueue(SETTLEMENTS + "?consumer.exclusive=true"));

            Session producerSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = producerSession.createProducer(
                    producerSession.createQueue(SETTLEMENTS));
            for (String batch : List.of("batch-1", "batch-2", "batch-3")) {
                producer.send(producerSession.createTextMessage(batch));
            }

            assertThat(receiveTexts(primary, 3)).containsExactly("batch-1", "batch-2", "batch-3");
            assertThat(standby.receiveNoWait()).isNull();

            primary.close();
            for (String batch : List.of("batch-4", "batch-5")) {
                producer.send(producerSession.createTextMessage(batch));
            }
            assertThat(receiveTexts(standby, 2)).containsExactly("batch-4", "batch-5");
            assertThat(standby.receiveNoWait()).isNull();
        } finally {
            connection.close();
        }
    }

    private static List<String> receiveTexts(MessageConsumer consumer, int count)
            throws JMSException {
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Message message = consumer.receive(RECEIVE_TIMEOUT_MILLIS);
            assertThat(message).as("settlement %d", i).isNotNull();
            texts.add(((TextMessage) message).getText());
        }
        return texts;
    }

    /// One account event as a worker observed it.
    record Delivery(String worker, String account, int sequence, String body,
            boolean firstForWorker) {
    }

    /// Applies account events delivered to its listener and records what it saw.
    static final class BalanceWorker {

        private final String name;

        private final CountDownLatch handled;

        private final List<Delivery> deliveries = Collections.synchronizedList(new ArrayList<>());

        BalanceWorker(String name, CountDownLatch handled) {
            this.name = name;
            this.handled = handled;
        }

        String name() {
            return name;
        }

        void listen(Connection connection) throws JMSException {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            session.createConsumer(session.createQueue(ACCOUNT_EVENTS)).setMessageListener(this::apply);
        }

        List<Delivery> deliveries() {
            synchronized (deliveries) {
                return List.copyOf(deliveries);
            }
        }

        private void apply(Message message) {
            try {
                deliveries.add(new Delivery(name,
                        message.getStringProperty("JMSXGroupID"),
                        message.getIntProperty("JMSXGroupSeq"),
                        ((TextMessage) message).getText(),
                        message.getBooleanProperty("JMSXGroupFirstForConsumer")));
                handled.countDown();
            } catch (JMSException e) {
                throw new IllegalStateException("Account event could not be applied", e);
            }
        }
    }
}
