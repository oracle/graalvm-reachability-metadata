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

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fans a stock price feed out over a JMS publish/subscribe topic through the classic Artemis
 * `Connection`/`Session` API: asynchronous `MessageListener` delivery, durable subscriptions
 * that survive a subscriber going offline, and JMS 2.0 shared subscriptions that load-balance.
 *
 * A market-data publisher sends `MapMessage` ticks; a dashboard listens asynchronously, an
 * audit service with a durable subscription catches up on ticks it missed while offline, and
 * two pricing workers split the feed through one shared subscription.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PriceFeedTopicSubscriptionTest {

    static {
        // Like the Artemis distribution, keep the broker audit log off: it is not the subject here.
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.activemq.audit", "warn");
    }

    private static final String PRICES = "market.prices";

    private static final String AUDIT_CLIENT = "audit-service";

    private static final String AUDIT_SUBSCRIPTION = "audit-log";

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
    void fansTicksOutToListenersDurableAndSharedSubscribers() throws Exception {
        List<Tick> morning = List.of(
                new Tick("ACME", new BigDecimal("101.25"), 300),
                new Tick("GLOBEX", new BigDecimal("47.10"), 1200),
                new Tick("ACME", new BigDecimal("101.40"), 150));
        List<Tick> afternoon = List.of(
                new Tick("INITECH", new BigDecimal("12.05"), 5000),
                new Tick("GLOBEX", new BigDecimal("46.95"), 800));

        try (Connection audit = connectionFactory.createConnection();
             Connection workers = connectionFactory.createConnection();
             Connection dashboard = connectionFactory.createConnection();
             Connection publisher = connectionFactory.createConnection()) {
            audit.setClientID(AUDIT_CLIENT);
            Session auditSession = audit.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic auditTopic = auditSession.createTopic(PRICES);
            TopicSubscriber auditLog = auditSession.createDurableSubscriber(auditTopic, AUDIT_SUBSCRIPTION);
            audit.start();

            int published = morning.size() + afternoon.size();
            TickCollector board = new TickCollector(new CountDownLatch(published));
            Session dashboardSession = dashboard.createSession(false, Session.AUTO_ACKNOWLEDGE);
            dashboardSession.createConsumer(dashboardSession.createTopic(PRICES)).setMessageListener(board);
            dashboard.start();

            CountDownLatch sharedDeliveries = new CountDownLatch(published);
            TickCollector workerOne = new TickCollector(sharedDeliveries);
            TickCollector workerTwo = new TickCollector(sharedDeliveries);
            Session workerOneSession = workers.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Session workerTwoSession = workers.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic workerTopic = workerOneSession.createTopic(PRICES);
            workerOneSession.createSharedConsumer(workerTopic, "pricing-workers").setMessageListener(workerOne);
            workerTwoSession.createSharedConsumer(workerTopic, "pricing-workers").setMessageListener(workerTwo);
            workers.start();

            Session publisherSession = publisher.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = publisherSession.createProducer(publisherSession.createTopic(PRICES));
            for (Tick tick : morning) {
                publish(publisherSession, producer, tick);
            }

            for (Tick expected : morning) {
                assertThat(readTick(auditLog.receive(10_000))).isEqualTo(expected);
            }
            auditLog.close();

            for (Tick tick : afternoon) {
                publish(publisherSession, producer, tick);
            }

            assertThat(board.await()).isTrue();
            List<Tick> all = new ArrayList<>(morning);
            all.addAll(afternoon);
            assertThat(board.ticks()).containsExactlyElementsOf(all);
            assertThat(board.failures()).isEmpty();

            assertThat(sharedDeliveries.await(10, TimeUnit.SECONDS)).isTrue();
            List<Tick> split = new ArrayList<>(workerOne.ticks());
            split.addAll(workerTwo.ticks());
            assertThat(split).containsExactlyInAnyOrderElementsOf(all);
            assertThat(workerOne.failures()).isEmpty();
            assertThat(workerTwo.failures()).isEmpty();
        }

        try (Connection audit = connectionFactory.createConnection()) {
            audit.setClientID(AUDIT_CLIENT);
            Session session = audit.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(PRICES);
            audit.start();
            try (TopicSubscriber auditLog = session.createDurableSubscriber(topic, AUDIT_SUBSCRIPTION)) {
                Message first = auditLog.receive(10_000);
                assertThat(readTick(first)).isEqualTo(afternoon.get(0));
                assertThat(first.getJMSDestination()).isEqualTo(topic);
                assertThat(readTick(auditLog.receive(10_000))).isEqualTo(afternoon.get(1));
                assertThat(auditLog.receiveNoWait()).isNull();
            }
            session.unsubscribe(AUDIT_SUBSCRIPTION);
        }
    }

    private void publish(Session session, MessageProducer producer, Tick tick) throws JMSException {
        MapMessage message = session.createMapMessage();
        message.setString("symbol", tick.symbol());
        message.setString("price", tick.price().toPlainString());
        message.setLong("volume", tick.volume());
        message.setStringProperty("exchange", "NYSE");
        producer.send(message);
    }

    private static Tick readTick(Message message) throws JMSException {
        assertThat(message).isInstanceOf(MapMessage.class);
        MapMessage map = (MapMessage) message;
        assertThat(map.getStringProperty("exchange")).isEqualTo("NYSE");
        return new Tick(map.getString("symbol"), new BigDecimal(map.getString("price")), map.getLong("volume"));
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** A price tick as the market-data publisher sends it. */
    record Tick(String symbol, BigDecimal price, long volume) {
    }

    /** An asynchronous subscriber that records every tick the broker delivers to it. */
    static final class TickCollector implements MessageListener {

        private final List<Tick> ticks = Collections.synchronizedList(new ArrayList<>());

        private final Map<String, Throwable> failures = new ConcurrentHashMap<>();

        private final CountDownLatch delivered;

        TickCollector(CountDownLatch delivered) {
            this.delivered = delivered;
        }

        @Override
        public void onMessage(Message message) {
            try {
                ticks.add(readTick(message));
            } catch (JMSException | AssertionError e) {
                failures.put(String.valueOf(ticks.size()), e);
            } finally {
                delivered.countDown();
            }
        }

        boolean await() throws InterruptedException {
            return delivered.await(10, TimeUnit.SECONDS);
        }

        List<Tick> ticks() {
            synchronized (ticks) {
                return new ArrayList<>(ticks);
            }
        }

        Map<String, Throwable> failures() {
            return failures;
        }
    }
}
