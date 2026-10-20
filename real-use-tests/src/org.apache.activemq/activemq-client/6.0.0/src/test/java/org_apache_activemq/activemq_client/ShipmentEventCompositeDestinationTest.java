/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fans shipment events out and back in through composite destinations: comma-separated
 * destination names parsed by the client into their `queue://` and `topic://` members, one
 * send delivered to several queues and a topic at once, and one consumer draining several
 * queues through a single composite subscription.
 *
 * A shipping desk publishes each tracking event once to a composite destination that feeds
 * the audit and billing queues and the live-tracking topic; a returns clerk then works the
 * European and American returns queues through one composite consumer.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShipmentEventCompositeDestinationTest {

    private static final long RECEIVE_TIMEOUT_MILLIS = 10_000L;

    private BrokerService broker;

    private ActiveMQConnectionFactory factory;

    @BeforeAll
    void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("shipping-broker");
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setUseShutdownHook(false);
        TransportConnector connector = broker.addConnector("tcp://127.0.0.1:0");
        broker.start();
        assertThat(broker.waitUntilStarted()).isTrue();
        factory = new ActiveMQConnectionFactory(connector.getConnectUri().toString());
    }

    @AfterAll
    void stopBroker() throws Exception {
        broker.stop();
        broker.waitUntilStopped();
    }

    @Test
    void fansOneSendOutToQueuesAndTopic() throws Exception {
        Connection connection = factory.createConnection();
        try {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue fanOut = session.createQueue(
                    "shipments.audit,shipments.billing,topic://shipments.live");

            ActiveMQDestination composite = (ActiveMQDestination) fanOut;
            assertThat(composite.isComposite()).isTrue();
            assertThat(composite.isQueue()).isTrue();
            List<String> members = new ArrayList<>();
            for (ActiveMQDestination member : composite.getCompositeDestinations()) {
                assertThat(member.isComposite()).isFalse();
                assertThat(member).isInstanceOf(member.isTopic() ? ActiveMQTopic.class
                        : ActiveMQQueue.class);
                members.add(member.getQualifiedName());
            }
            assertThat(members).containsExactlyInAnyOrder(
                    "queue://shipments.audit", "queue://shipments.billing",
                    "topic://shipments.live");

            Topic live = session.createTopic("shipments.live");
            MessageConsumer tracker = session.createConsumer(live);
            MessageProducer desk = session.createProducer(fanOut);
            for (String event : List.of("PICKED_UP", "IN_TRANSIT", "DELIVERED")) {
                TextMessage message = session.createTextMessage(event);
                message.setStringProperty("trackingNumber", "1Z-4471");
                desk.send(message);
            }

            assertThat(receiveEvents(tracker, 3)).containsExactly(
                    "PICKED_UP", "IN_TRANSIT", "DELIVERED");
            for (String queueName : List.of("shipments.audit", "shipments.billing")) {
                Queue queue = session.createQueue(queueName);
                try (MessageConsumer consumer = session.createConsumer(queue)) {
                    assertThat(receiveEvents(consumer, 3)).as(queueName).containsExactly(
                            "PICKED_UP", "IN_TRANSIT", "DELIVERED");
                }
                assertThat(browse(session, queue)).as(queueName).isEmpty();
            }
            tracker.close();
        } finally {
            connection.close();
        }
    }

    @Test
    void drainsSeveralQueuesThroughOneCompositeConsumer() throws Exception {
        Connection connection = factory.createConnection();
        try {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue europe = session.createQueue("returns.eu");
            Queue america = session.createQueue("returns.us");
            MessageProducer clerkDesk = session.createProducer(null);
            clerkDesk.send(europe, session.createTextMessage("RMA-EU-1"));
            clerkDesk.send(america, session.createTextMessage("RMA-US-1"));
            clerkDesk.send(europe, session.createTextMessage("RMA-EU-2"));
            clerkDesk.send(america, session.createTextMessage("RMA-US-2"));

            ActiveMQQueue allReturns = new ActiveMQQueue("returns.eu,returns.us");
            assertThat(allReturns.getCompositeDestinations())
                    .containsExactlyInAnyOrder(
                            new ActiveMQQueue("returns.eu"), new ActiveMQQueue("returns.us"));

            List<String> handled = new ArrayList<>();
            List<Destination> origins = new ArrayList<>();
            try (MessageConsumer clerk = session.createConsumer(allReturns)) {
                for (int i = 0; i < 4; i++) {
                    Message message = clerk.receive(RECEIVE_TIMEOUT_MILLIS);
                    assertThat(message).as("return %d", i).isNotNull();
                    handled.add(((TextMessage) message).getText());
                    origins.add(message.getJMSDestination());
                }
                assertThat(clerk.receiveNoWait()).isNull();
            }

            assertThat(handled).containsExactlyInAnyOrder(
                    "RMA-EU-1", "RMA-EU-2", "RMA-US-1", "RMA-US-2");
            assertThat(handled.stream().filter(rma -> rma.startsWith("RMA-EU")))
                    .containsExactly("RMA-EU-1", "RMA-EU-2");
            assertThat(handled.stream().filter(rma -> rma.startsWith("RMA-US")))
                    .containsExactly("RMA-US-1", "RMA-US-2");
            for (int i = 0; i < handled.size(); i++) {
                Queue expected = handled.get(i).startsWith("RMA-EU") ? europe : america;
                assertThat(origins.get(i)).as(handled.get(i)).isEqualTo(expected);
            }
            assertThat(browse(session, europe)).isEmpty();
            assertThat(browse(session, america)).isEmpty();
        } finally {
            connection.close();
        }
    }

    /// Receives `count` events in arrival order, failing if one does not arrive in time.
    private static List<String> receiveEvents(MessageConsumer consumer, int count)
            throws JMSException {
        List<String> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Message message = consumer.receive(RECEIVE_TIMEOUT_MILLIS);
            assertThat(message).as("event %d", i).isNotNull();
            assertThat(message.getStringProperty("trackingNumber")).isEqualTo("1Z-4471");
            events.add(((TextMessage) message).getText());
        }
        return events;
    }

    /// Lists the bodies of the messages still waiting on `queue`.
    private static List<String> browse(Session session, Queue queue) throws JMSException {
        List<String> bodies = new ArrayList<>();
        try (QueueBrowser browser = session.createBrowser(queue)) {
            Enumeration<?> pending = browser.getEnumeration();
            while (pending.hasMoreElements()) {
                bodies.add(((TextMessage) pending.nextElement()).getText());
            }
        }
        return bodies;
    }
}
