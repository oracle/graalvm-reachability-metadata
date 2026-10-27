/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Connection;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageFormatException;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQMessageConsumer;
import org.apache.activemq.ActiveMQMessageProducer;
import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.MessageTransformerSupport;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.command.ActiveMQQueue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Converts freight rate quotes through the client's message transformer hooks: a
 * `MessageTransformer` installed on the connection factory and inherited by connections,
 * sessions, producers and consumers, overridden per session and per consumer, encoding
 * `MapMessage` quotes as JSON text on the wire and decoding them back with
 * `MessageTransformerSupport.copyProperties`.
 *
 * The quoting desk builds quotes as map messages, but the shared queue must stay readable by
 * non-Java rating services, so the transformer turns every outgoing map into a JSON text
 * message and every incoming JSON quote back into a map. A plain connection without a
 * transformer shows what actually travels; the transformer also refuses quotes without a
 * lane, and client acknowledgement and recovery keep working on decoded messages.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FreightQuoteMessageTransformerTest {

    private static final String QUOTES = "freight.quotes";

    private static final String JSON = "application/json";

    private final ObjectMapper mapper = new ObjectMapper();

    private BrokerService broker;

    private String brokerUrl;

    @BeforeAll
    void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("freight-quote-broker");
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
    void encodesQuotesAsJsonOnTheWireAndDecodesThemForTheDesk() throws Exception {
        JsonQuoteEnvelope envelope = new JsonQuoteEnvelope(mapper);
        ActiveMQConnectionFactory deskFactory = new ActiveMQConnectionFactory(brokerUrl);
        deskFactory.setTransformer(envelope);
        ActiveMQConnectionFactory rawFactory = new ActiveMQConnectionFactory(brokerUrl);
        assertThat(rawFactory.getTransformer()).isNull();

        ActiveMQConnection desk = (ActiveMQConnection) deskFactory.createConnection();
        Connection raw = rawFactory.createConnection();
        try {
            desk.start();
            raw.start();
            assertThat(desk.getTransformer()).isSameAs(envelope);

            ActiveMQSession deskSession =
                    (ActiveMQSession) desk.createSession(false, Session.AUTO_ACKNOWLEDGE);
            assertThat(deskSession.getTransformer()).isSameAs(envelope);
            Queue quotes = deskSession.createQueue(QUOTES);
            ActiveMQMessageProducer producer =
                    (ActiveMQMessageProducer) deskSession.createProducer(quotes);
            assertThat(producer.getTransformer()).isSameAs(envelope);

            producer.send(quote(deskSession, "Q-1", "RTM-HAM", "Nordline", 1250.5, 3),
                    DeliveryMode.PERSISTENT, 7, 0L);
            assertThat(envelope.encoded.get()).isEqualTo(1);

            Session rawSession = raw.createSession(false, Session.AUTO_ACKNOWLEDGE);
            try (MessageConsumer rawConsumer = rawSession.createConsumer(quotes)) {
                Message onTheWire = rawConsumer.receive(10_000L);
                assertThat(onTheWire).isInstanceOf(TextMessage.class);
                assertThat(onTheWire.getStringProperty("contentType")).isEqualTo(JSON);
                assertThat(onTheWire.getStringProperty("region")).isEqualTo("emea");
                assertThat(onTheWire.getJMSCorrelationID()).isEqualTo("Q-1");
                assertThat(onTheWire.getJMSPriority()).isEqualTo(7);
                assertThat(onTheWire.getJMSDeliveryMode()).isEqualTo(DeliveryMode.PERSISTENT);
                Map<String, Object> body = mapper.readValue(((TextMessage) onTheWire).getText(),
                        new TypeReference<Map<String, Object>>() { });
                assertThat(body).containsOnly(
                        Map.entry("lane", "RTM-HAM"),
                        Map.entry("carrier", "Nordline"),
                        Map.entry("rate", 1250.5),
                        Map.entry("containers", 3),
                        Map.entry("hazardous", false));
            }

            producer.send(quote(deskSession, "Q-2", "ANR-GDN", "Baltic Feeder", 980.25, 1));
            Session reviewSession = desk.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            try (MessageConsumer review = reviewSession.createConsumer(quotes)) {
                assertThat(((ActiveMQMessageConsumer) review).getTransformer()).isSameAs(envelope);

                MapMessage first = (MapMessage) review.receive(10_000L);
                assertThat(first).isNotNull();
                assertThat(entryNames(first))
                        .containsExactlyInAnyOrder("lane", "carrier", "rate", "containers",
                                "hazardous");
                assertThat(first.getString("lane")).isEqualTo("ANR-GDN");
                assertThat(first.getString("carrier")).isEqualTo("Baltic Feeder");
                assertThat(first.getDouble("rate")).isEqualTo(980.25);
                assertThat(first.getInt("containers")).isEqualTo(1);
                assertThat(first.getBoolean("hazardous")).isFalse();
                assertThat(first.getJMSCorrelationID()).isEqualTo("Q-2");
                assertThat(first.getJMSMessageID()).startsWith("ID:");
                assertThat(first.getJMSDestination()).isEqualTo(new ActiveMQQueue(QUOTES));
                assertThat(first.getStringProperty("region")).isEqualTo("emea");
                assertThat(first.getJMSRedelivered()).isFalse();
                assertThat(envelope.decoded.get()).isEqualTo(1);

                reviewSession.recover();
                MapMessage again = (MapMessage) review.receive(10_000L);
                assertThat(again).isNotNull();
                assertThat(again.getJMSMessageID()).isEqualTo(first.getJMSMessageID());
                assertThat(again.getJMSRedelivered()).isTrue();
                assertThat(again.getString("lane")).isEqualTo("ANR-GDN");
                assertThat(envelope.decoded.get()).isEqualTo(2);
                again.acknowledge();
            }
            assertThat(queued(rawSession, quotes)).isZero();
        } finally {
            desk.close();
            raw.close();
        }
    }

    @Test
    void sessionAndConsumerOverridesBypassTheEnvelopeAndInvalidQuotesAreRefused()
            throws Exception {
        JsonQuoteEnvelope envelope = new JsonQuoteEnvelope(mapper);
        ActiveMQConnectionFactory deskFactory = new ActiveMQConnectionFactory(brokerUrl);
        deskFactory.setTransformer(envelope);
        Queue overrides = new ActiveMQQueue(QUOTES + ".overrides");

        ActiveMQConnection desk = (ActiveMQConnection) deskFactory.createConnection();
        try {
            desk.start();
            ActiveMQSession deskSession =
                    (ActiveMQSession) desk.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer encoding = deskSession.createProducer(overrides);

            assertThatThrownBy(() -> encoding.send(
                    quote(deskSession, "Q-3", null, "Nordline", 410.0, 2)))
                    .isInstanceOf(MessageFormatException.class)
                    .hasMessageContaining("Q-3");
            encoding.send(quote(deskSession, "Q-4", "HAM-OSL", "Nordline", 640.0, 2));

            ActiveMQSession legacySession =
                    (ActiveMQSession) desk.createSession(false, Session.AUTO_ACKNOWLEDGE);
            legacySession.setTransformer(null);
            ActiveMQMessageProducer legacy =
                    (ActiveMQMessageProducer) legacySession.createProducer(overrides);
            assertThat(legacy.getTransformer()).isNull();
            legacy.send(quote(legacySession, "Q-5", "OSL-RTM", "Fjord Line", 720.0, 4));
            assertThat(envelope.encoded.get()).isEqualTo(1);

            try (MessageConsumer decoding = deskSession.createConsumer(overrides)) {
                Message encoded = decoding.receive(10_000L);
                assertThat(encoded).isInstanceOf(MapMessage.class);
                assertThat(encoded.getJMSCorrelationID()).isEqualTo("Q-4");
                assertThat(((MapMessage) encoded).getString("lane")).isEqualTo("HAM-OSL");

                Message passedThrough = decoding.receive(10_000L);
                assertThat(passedThrough).isInstanceOf(MapMessage.class);
                assertThat(passedThrough.getJMSCorrelationID()).isEqualTo("Q-5");
                assertThat(passedThrough.getStringProperty("contentType")).isNull();
                assertThat(((MapMessage) passedThrough).getInt("containers")).isEqualTo(4);
                assertThat(envelope.decoded.get()).isEqualTo(1);
            }

            encoding.send(quote(deskSession, "Q-6", "GDN-ANR", "Baltic Feeder", 515.75, 1));
            try (MessageConsumer auditor = deskSession.createConsumer(overrides)) {
                ((ActiveMQMessageConsumer) auditor).setTransformer(null);
                Message audited = auditor.receive(10_000L);
                assertThat(audited).isInstanceOf(TextMessage.class);
                assertThat(audited.getJMSCorrelationID()).isEqualTo("Q-6");
                assertThat(mapper.readTree(((TextMessage) audited).getText())
                        .get("carrier").asText()).isEqualTo("Baltic Feeder");
                assertThat(envelope.decoded.get()).isEqualTo(1);
            }
            assertThat(queued(deskSession, overrides)).isZero();
        } finally {
            desk.close();
        }
    }

    /// Builds one quote as the desk does: a map message keyed by its quote ID.
    private static MapMessage quote(Session session, String id, String lane, String carrier,
            double rate, int containers) throws JMSException {
        MapMessage quote = session.createMapMessage();
        if (lane != null) {
            quote.setString("lane", lane);
        }
        quote.setString("carrier", carrier);
        quote.setDouble("rate", rate);
        quote.setInt("containers", containers);
        quote.setBoolean("hazardous", false);
        quote.setJMSCorrelationID(id);
        quote.setStringProperty("region", "emea");
        return quote;
    }

    /// Lists the entry names of a map message in enumeration order.
    private static List<String> entryNames(MapMessage map) throws JMSException {
        List<String> names = new ArrayList<>();
        Enumeration<?> entries = map.getMapNames();
        while (entries.hasMoreElements()) {
            names.add(entries.nextElement().toString());
        }
        return names;
    }

    /// Counts the messages still waiting on `queue`.
    private static int queued(Session session, Queue queue) throws JMSException {
        int count = 0;
        try (QueueBrowser browser = session.createBrowser(queue)) {
            Enumeration<?> waiting = browser.getEnumeration();
            while (waiting.hasMoreElements()) {
                waiting.nextElement();
                count++;
            }
        }
        return count;
    }

    /// Encodes outgoing map quotes as JSON text and decodes incoming JSON quotes into maps.
    static final class JsonQuoteEnvelope extends MessageTransformerSupport {

        private final ObjectMapper mapper;

        final AtomicInteger encoded = new AtomicInteger();

        final AtomicInteger decoded = new AtomicInteger();

        JsonQuoteEnvelope(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public Message producerTransform(Session session, MessageProducer producer,
                Message message) throws JMSException {
            if (!(message instanceof MapMessage map)) {
                return null;
            }
            if (!map.itemExists("lane")) {
                throw new MessageFormatException(
                        "quote " + map.getJMSCorrelationID() + " has no lane");
            }
            Map<String, Object> body = new LinkedHashMap<>();
            for (String name : entryNames(map)) {
                body.put(name, map.getObject(name));
            }
            TextMessage text;
            try {
                text = session.createTextMessage(mapper.writeValueAsString(body));
            } catch (IOException e) {
                throw new MessageFormatException(e.getMessage());
            }
            copyProperties(map, text);
            text.setStringProperty("contentType", JSON);
            encoded.incrementAndGet();
            return text;
        }

        @Override
        public Message consumerTransform(Session session, MessageConsumer consumer,
                Message message) throws JMSException {
            if (!(message instanceof TextMessage text)
                    || !JSON.equals(text.getStringProperty("contentType"))) {
                return null;
            }
            Map<String, Object> body;
            try {
                body = mapper.readValue(text.getText(),
                        new TypeReference<Map<String, Object>>() { });
            } catch (IOException e) {
                throw new MessageFormatException(e.getMessage());
            }
            MapMessage map = session.createMapMessage();
            for (Map.Entry<String, Object> entry : body.entrySet()) {
                map.setObject(entry.getKey(), entry.getValue());
            }
            copyProperties(text, map);
            decoded.incrementAndGet();
            return map;
        }
    }
}
