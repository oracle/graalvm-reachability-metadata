/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Connection;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Routes warehouse orders through point-to-point queue messaging over the OpenWire TCP
 * transport: a connection factory, a persistent producer, selector-filtered consumers,
 * client acknowledgement, and a queue browser.
 *
 * An order desk publishes JSON orders tagged with a `region` property to one queue; each
 * regional warehouse consumes only its own orders through a message selector and
 * acknowledges them once handled, and a browser confirms that nothing is left behind.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderFulfilmentQueueTest {

    private static final String ORDERS = "warehouse.orders";

    private final ObjectMapper mapper = new ObjectMapper();

    private BrokerService broker;

    private String brokerUrl;

    @BeforeAll
    void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("order-desk-broker");
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setUseShutdownHook(false);
        TransportConnector connector = broker.addConnector("tcp://127.0.0.1:0");
        broker.start();
        assertThat(broker.waitUntilStarted()).isTrue();
        brokerUrl = connector.getConnectUri().toString();
    }

    @AfterAll
    void stopBroker() throws Exception {
        broker.stop();
        broker.waitUntilStopped();
    }

    @Test
    void routesOrdersToRegionalWarehouses() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        factory.setClientIDPrefix("order-desk");
        List<Order> placed = List.of(
                new Order("A-100", "north", "pallet-jack", 2),
                new Order("A-101", "south", "shrink-wrap", 40),
                new Order("A-102", "north", "label-printer", 1),
                new Order("A-103", "south", "barcode-scanner", 3),
                new Order("A-104", "north", "forklift-battery", 1));

        Connection connection = factory.createConnection();
        try {
            connection.start();
            ActiveMQConnection openWire = (ActiveMQConnection) connection;
            assertThat(openWire.getTransport().getRemoteAddress()).contains("127.0.0.1");
            assertThat(connection.getClientID()).startsWith("order-desk");

            Session desk = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue orders = desk.createQueue(ORDERS);
            MessageProducer producer = desk.createProducer(orders);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            for (Order order : placed) {
                TextMessage message = desk.createTextMessage(mapper.writeValueAsString(order));
                message.setStringProperty("region", order.region());
                message.setIntProperty("quantity", order.quantity());
                message.setJMSCorrelationID(order.id());
                producer.send(message);
            }
            assertThat(browse(desk, orders, null)).hasSize(placed.size());
            assertThat(browse(desk, orders, "region = 'south'"))
                    .containsExactly("A-101", "A-103");

            Session north = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            List<Order> northOrders = drain(north, north.createQueue(ORDERS), "region = 'north'", 3);
            assertThat(northOrders).extracting(Order::id).containsExactly("A-100", "A-102", "A-104");
            assertThat(browse(desk, orders, null)).containsExactly("A-101", "A-103");

            Session south = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            List<Order> southOrders = drain(south, south.createQueue(ORDERS),
                    "region = 'south' AND quantity > 10", 1);
            assertThat(southOrders).containsExactly(placed.get(1));
            assertThat(browse(desk, orders, null)).containsExactly("A-103");

            List<Order> remainder = drain(south, south.createQueue(ORDERS), "region = 'south'", 1);
            assertThat(remainder).containsExactly(placed.get(3));
            assertThat(browse(desk, orders, null)).isEmpty();
        } finally {
            connection.close();
        }
        assertThat(((ActiveMQConnection) connection).isClosed()).isTrue();
    }

    /// Receives `count` orders matching `selector` and acknowledges them together once handled.
    private List<Order> drain(Session session, Queue queue, String selector, int count)
            throws Exception {
        List<Order> received = new ArrayList<>();
        Message last = null;
        try (MessageConsumer consumer = session.createConsumer(queue, selector)) {
            for (int i = 0; i < count; i++) {
                Message message = consumer.receive(10_000L);
                assertThat(message).as("order %d matching %s", i, selector).isNotNull();
                TextMessage text = (TextMessage) message;
                Order order = mapper.readValue(text.getText(), Order.class);
                assertThat(text.getJMSCorrelationID()).isEqualTo(order.id());
                assertThat(text.getStringProperty("region")).isEqualTo(order.region());
                assertThat(text.getJMSDeliveryMode()).isEqualTo(DeliveryMode.PERSISTENT);
                assertThat(text.getJMSRedelivered()).isFalse();
                received.add(order);
                last = message;
            }
            last.acknowledge();
        }
        return received;
    }

    /// Lists the correlation IDs of the orders still queued, in delivery order.
    private static List<String> browse(Session session, Queue queue, String selector)
            throws JMSException {
        List<String> ids = new ArrayList<>();
        try (QueueBrowser browser = selector == null
                ? session.createBrowser(queue)
                : session.createBrowser(queue, selector)) {
            Enumeration<?> queued = browser.getEnumeration();
            while (queued.hasMoreElements()) {
                ids.add(((Message) queued.nextElement()).getJMSCorrelationID());
            }
        }
        return ids;
    }

    /// One warehouse order, carried as the JSON body of a text message.
    record Order(String id, String region, String item, int quantity) {
    }
}
