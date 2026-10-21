/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import jakarta.jms.BytesMessage;
import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.InvalidDestinationException;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TemporaryQueue;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.command.ActiveMQTempQueue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Answers stock lookups through request/reply messaging over temporary queues: a reply-to
 * temporary destination, correlation IDs that pair replies with requests, binary
 * bytes-message payloads, and the temporary queue's deletion rules.
 *
 * A storefront asks an inventory service to reserve stock for several SKUs; the service
 * listens on a shared request queue, reserves what it can, and answers each request on the
 * storefront's private temporary queue, which the storefront deletes once it is done.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryLookupRequestReplyTest {

    private static final String LOOKUPS = "inventory.lookups";

    private static final long RECEIVE_TIMEOUT_MILLIS = 10_000L;

    private BrokerService broker;

    private ActiveMQConnectionFactory factory;

    @BeforeAll
    void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("inventory-broker");
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
    void reservesStockThroughTemporaryReplyQueue() throws Exception {
        Connection serviceConnection = factory.createConnection();
        Connection storefrontConnection = factory.createConnection();
        try {
            InventoryService service = new InventoryService(Map.of("KB-101", 5, "MS-202", 1));
            service.listen(serviceConnection);
            serviceConnection.start();
            storefrontConnection.start();

            Session storefront = storefrontConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            TemporaryQueue replies = storefront.createTemporaryQueue();
            assertThat(replies).isInstanceOf(ActiveMQTempQueue.class);
            assertThat(replies.getQueueName()).isNotBlank();

            List<Reservation> requested = List.of(
                    new Reservation("KB-101", 3),
                    new Reservation("MS-202", 2),
                    new Reservation("KB-101", 2),
                    new Reservation("UNKNOWN", 1));
            MessageProducer producer = storefront.createProducer(storefront.createQueue(LOOKUPS));
            for (int i = 0; i < requested.size(); i++) {
                Reservation reservation = requested.get(i);
                BytesMessage request = storefront.createBytesMessage();
                request.writeUTF(reservation.sku());
                request.writeInt(reservation.quantity());
                request.setJMSReplyTo(replies);
                request.setJMSCorrelationID("lookup-" + i);
                producer.send(request);
            }

            Map<String, Answer> answers = new LinkedHashMap<>();
            MessageConsumer replyConsumer = storefront.createConsumer(replies);
            for (int i = 0; i < requested.size(); i++) {
                Message message = replyConsumer.receive(RECEIVE_TIMEOUT_MILLIS);
                assertThat(message).as("reply %d", i).isNotNull();
                assertThat(message.getJMSDestination()).isEqualTo(replies);
                BytesMessage reply = (BytesMessage) message;
                answers.put(reply.getJMSCorrelationID(),
                        new Answer(reply.readUTF(), reply.readBoolean(), reply.readInt()));
            }
            assertThat(replyConsumer.receiveNoWait()).isNull();

            assertThat(answers).containsExactly(
                    Map.entry("lookup-0", new Answer("KB-101", true, 2)),
                    Map.entry("lookup-1", new Answer("MS-202", false, 1)),
                    Map.entry("lookup-2", new Answer("KB-101", true, 0)),
                    Map.entry("lookup-3", new Answer("UNKNOWN", false, 0)));
            assertThat(service.stock()).containsExactlyInAnyOrderEntriesOf(
                    Map.of("KB-101", 0, "MS-202", 1));

            assertThatThrownBy(replies::delete).isInstanceOf(JMSException.class);
            replyConsumer.close();
            replies.delete();
            BytesMessage late = storefront.createBytesMessage();
            late.writeUTF("KB-101");
            MessageProducer anonymous = storefront.createProducer(null);
            assertThatThrownBy(() -> anonymous.send(replies, late))
                    .isInstanceOf(InvalidDestinationException.class);
        } finally {
            storefrontConnection.close();
            serviceConnection.close();
        }
    }

    /// One reservation the storefront asks for.
    record Reservation(String sku, int quantity) {
    }

    /// The service's answer: whether the reservation succeeded and the stock left afterwards.
    record Answer(String sku, boolean reserved, int remaining) {
    }

    /// Reserves stock for each request it hears and replies to the requester's reply-to queue.
    static final class InventoryService {

        private final Map<String, Integer> stock;

        private Session session;

        private MessageProducer replier;

        InventoryService(Map<String, Integer> initialStock) {
            stock = new HashMap<>(initialStock);
        }

        void listen(Connection connection) throws JMSException {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue lookups = session.createQueue(LOOKUPS);
            replier = session.createProducer(null);
            session.createConsumer(lookups).setMessageListener(this::answer);
        }

        synchronized Map<String, Integer> stock() {
            return new HashMap<>(stock);
        }

        private synchronized void answer(Message message) {
            try {
                BytesMessage request = (BytesMessage) message;
                String sku = request.readUTF();
                int quantity = request.readInt();
                int available = stock.getOrDefault(sku, 0);
                boolean reserved = stock.containsKey(sku) && available >= quantity;
                int remaining = reserved ? available - quantity : available;
                if (reserved) {
                    stock.put(sku, remaining);
                }

                BytesMessage reply = session.createBytesMessage();
                reply.writeUTF(sku);
                reply.writeBoolean(reserved);
                reply.writeInt(remaining);
                reply.setJMSCorrelationID(request.getJMSCorrelationID());
                Destination replyTo = request.getJMSReplyTo();
                replier.send(replyTo, reply);
            } catch (JMSException e) {
                throw new IllegalStateException("Inventory lookup failed", e);
            }
        }
    }
}
