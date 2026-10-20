/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.Topic;
import jakarta.jms.TopicSubscriber;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.command.ActiveMQTopic;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fans market quotes out over publish/subscribe topics: wildcard topic subscriptions with
 * an asynchronous message listener, durable subscriptions that keep quotes for an offline
 * client, and typed map-message payloads.
 *
 * A pricing feed publishes equity and FX quotes to per-instrument topics; a dashboard
 * listens to every instrument through a `prices.>` wildcard, while an audit service with a
 * durable subscription to one equity goes offline, comes back, and catches up on what it
 * missed before unsubscribing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MarketDataTopicSubscriptionTest {

    private static final String ACME = "prices.equity.ACME";

    private static final String EURUSD = "prices.fx.EURUSD";

    private static final String AUDIT_CLIENT = "audit-service";

    private static final String AUDIT_SUBSCRIPTION = "acme-audit";

    private BrokerService broker;

    private ActiveMQConnectionFactory factory;

    @BeforeAll
    void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("pricing-broker");
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setUseShutdownHook(false);
        broker.start();
        assertThat(broker.waitUntilStarted()).isTrue();
        factory = new ActiveMQConnectionFactory("vm://pricing-broker?create=false");
    }

    @AfterAll
    void stopBroker() throws Exception {
        broker.stop();
        broker.waitUntilStopped();
    }

    @Test
    void deliversLiveQuotesToListenersAndKeepsMissedQuotesForDurableSubscriber()
            throws Exception {
        registerAuditSubscription();

        Connection feed = factory.createConnection();
        try {
            feed.start();
            Session dashboardSession = feed.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic everyInstrument = dashboardSession.createTopic("prices.>");
            assertThat(((ActiveMQTopic) everyInstrument).isPattern()).isTrue();
            CountDownLatch dashboardFilled = new CountDownLatch(3);
            List<Quote> dashboard = Collections.synchronizedList(new ArrayList<>());
            MessageConsumer dashboardConsumer = dashboardSession.createConsumer(everyInstrument);
            dashboardConsumer.setMessageListener(message -> {
                try {
                    dashboard.add(Quote.from((MapMessage) message));
                } catch (JMSException e) {
                    throw new IllegalStateException("Unreadable quote " + message, e);
                }
                dashboardFilled.countDown();
            });

            Session publisher = feed.createSession(false, Session.AUTO_ACKNOWLEDGE);
            try (MessageProducer producer = publisher.createProducer(null)) {
                publish(publisher, producer, new Quote(ACME, "ACME", 10_125L, 300));
                publish(publisher, producer, new Quote(EURUSD, "EURUSD", 10_842L, 1_000_000));
                publish(publisher, producer, new Quote(ACME, "ACME", 10_150L, 200));
            }

            assertThat(dashboardFilled.await(10L, TimeUnit.SECONDS))
                    .as("dashboard quotes %s", dashboard).isTrue();
            assertThat(dashboard).containsExactlyInAnyOrder(
                    new Quote(ACME, "ACME", 10_125L, 300),
                    new Quote(EURUSD, "EURUSD", 10_842L, 1_000_000),
                    new Quote(ACME, "ACME", 10_150L, 200));
            dashboardConsumer.close();

            Session lateSession = feed.createSession(false, Session.AUTO_ACKNOWLEDGE);
            try (MessageConsumer late = lateSession.createConsumer(lateSession.createTopic(ACME))) {
                assertThat(late.receive(500L))
                        .as("a non-durable subscriber never sees quotes published before it")
                        .isNull();
            }
        } finally {
            feed.close();
        }

        assertThat(catchUpAndUnsubscribe()).containsExactly(
                new Quote(ACME, "ACME", 10_125L, 300),
                new Quote(ACME, "ACME", 10_150L, 200));
    }

    /// Creates the audit service's durable subscription and leaves it offline.
    private void registerAuditSubscription() throws JMSException {
        Connection audit = auditConnection();
        try {
            Session session = audit.createSession(false, Session.AUTO_ACKNOWLEDGE);
            TopicSubscriber subscriber =
                    session.createDurableSubscriber(session.createTopic(ACME), AUDIT_SUBSCRIPTION);
            assertThat(subscriber.getTopic().getTopicName()).isEqualTo(ACME);
            subscriber.close();
        } finally {
            audit.close();
        }
    }

    /// Reconnects the audit service, drains the quotes it missed, and drops the subscription.
    private List<Quote> catchUpAndUnsubscribe() throws JMSException {
        Connection audit = auditConnection();
        try {
            audit.start();
            Session session = audit.createSession(false, Session.AUTO_ACKNOWLEDGE);
            List<Quote> missed = new ArrayList<>();
            Topic acme = session.createTopic(ACME);
            try (TopicSubscriber subscriber =
                    session.createDurableSubscriber(acme, AUDIT_SUBSCRIPTION)) {
                for (Message message = subscriber.receive(10_000L);
                        message != null;
                        message = subscriber.receive(500L)) {
                    missed.add(Quote.from((MapMessage) message));
                }
            }
            session.unsubscribe(AUDIT_SUBSCRIPTION);
            return missed;
        } finally {
            audit.close();
        }
    }

    private Connection auditConnection() throws JMSException {
        Connection audit = factory.createConnection();
        audit.setClientID(AUDIT_CLIENT);
        assertThat(audit.getClientID()).isEqualTo(AUDIT_CLIENT);
        return audit;
    }

    private static void publish(Session session, MessageProducer producer, Quote quote)
            throws JMSException {
        MapMessage message = session.createMapMessage();
        message.setString("symbol", quote.symbol());
        message.setLong("priceTicks", quote.priceTicks());
        message.setInt("size", quote.size());
        producer.send(session.createTopic(quote.topic()), message);
    }

    /// One quote as the feed publishes it and a subscriber reads it back.
    record Quote(String topic, String symbol, long priceTicks, int size) {

        static Quote from(MapMessage message) throws JMSException {
            String topic = ((Topic) message.getJMSDestination()).getTopicName();
            return new Quote(topic, message.getString("symbol"),
                    message.getLong("priceTicks"), message.getInt("size"));
        }
    }
}
