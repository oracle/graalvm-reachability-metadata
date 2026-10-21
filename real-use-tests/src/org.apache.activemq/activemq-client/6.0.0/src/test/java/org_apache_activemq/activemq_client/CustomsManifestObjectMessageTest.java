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
import jakarta.jms.MessageEOFException;
import jakarta.jms.MessageFormatException;
import jakarta.jms.MessageNotWriteableException;
import jakarta.jms.MessageProducer;
import jakarta.jms.ObjectMessage;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;
import jakarta.jms.StreamMessage;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exchanges customs paperwork through Java-serialized object messages guarded by the
 * client's trusted-package allowlist, and through typed stream messages: `ObjectMessage`
 * payloads of application, `java.lang` and `java.util` types, deserialization refused for
 * untrusted packages, per-factory and per-connection trust settings, snapshot-on-set
 * semantics, and `StreamMessage` field conversions, end-of-stream and read-only rules.
 *
 * A freight forwarder ships serializable manifests to a customs queue. A legacy auditor
 * that keeps the default allowlist can browse them but cannot open them, while the
 * customs office, which trusts the forwarder's package, reads every manifest. Duty
 * declarations travel as stream messages of ordered typed fields.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomsManifestObjectMessageTest {

    private static final String MANIFESTS = "customs.manifests";

    private static final String TALLIES = "customs.tallies";

    private static final String DECLARATIONS = "customs.declarations";

    private static final String FORWARDER_PACKAGE = Manifest.class.getPackageName();

    private BrokerService broker;

    private String brokerUrl;

    @BeforeAll
    void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("customs-broker");
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
    void customsOfficeReadsManifestsOnlyFromTrustedPackages() throws Exception {
        Manifest rotterdam = new Manifest("MF-2001", "Rotterdam", 18_400L,
                new ArrayList<>(List.of("CONT-001", "CONT-002")));
        Manifest hamburg = new Manifest("MF-2002", "Hamburg", 7_250L,
                new ArrayList<>(List.of("CONT-310")));

        ActiveMQConnectionFactory forwarderFactory = new ActiveMQConnectionFactory(brokerUrl);
        Connection forwarder = forwarderFactory.createConnection();
        try {
            Session session = forwarder.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(session.createQueue(MANIFESTS));
            for (Manifest manifest : List.of(rotterdam, hamburg)) {
                ObjectMessage message = session.createObjectMessage(manifest);
                message.setJMSCorrelationID(manifest.id());
                assertThat(message.getObject()).isSameAs(manifest);
                producer.send(message);
            }
            // The body was serialized when it was set, so a late edit never reaches customs.
            rotterdam.containers().add("CONT-SMUGGLED");
        } finally {
            forwarder.close();
        }

        ActiveMQConnectionFactory auditorFactory = new ActiveMQConnectionFactory(brokerUrl);
        assertThat(auditorFactory.isTrustAllPackages()).isFalse();
        Connection auditor = auditorFactory.createConnection();
        try {
            auditor.start();
            Session session = auditor.createSession(false, Session.AUTO_ACKNOWLEDGE);
            List<ObjectMessage> browsed = browse(session, session.createQueue(MANIFESTS));
            assertThat(browsed).extracting(Message::getJMSCorrelationID)
                    .containsExactly("MF-2001", "MF-2002");
            for (ObjectMessage message : browsed) {
                assertThatThrownBy(message::getObject)
                        .isInstanceOf(JMSException.class)
                        .hasCauseInstanceOf(ClassNotFoundException.class)
                        .hasMessageContaining(Manifest.class.getName())
                        .hasMessageContaining("not trusted");
            }
        } finally {
            auditor.close();
        }

        ActiveMQConnectionFactory customsFactory = new ActiveMQConnectionFactory(brokerUrl);
        customsFactory.setTrustedPackages(List.of(FORWARDER_PACKAGE, "java.util"));
        Connection customs = customsFactory.createConnection();
        try {
            customs.start();
            assertThat(((ActiveMQConnection) customs).getTrustedPackages())
                    .containsExactly(FORWARDER_PACKAGE, "java.util");
            Session session = customs.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            List<Manifest> cleared = new ArrayList<>();
            Message last = null;
            Queue manifests = session.createQueue(MANIFESTS);
            try (MessageConsumer consumer = session.createConsumer(manifests)) {
                for (int i = 0; i < 2; i++) {
                    Message received = consumer.receive(10_000L);
                    assertThat(received).as("manifest %d", i).isInstanceOf(ObjectMessage.class);
                    ObjectMessage message = (ObjectMessage) received;
                    assertThat(message.isBodyAssignableTo(Manifest.class)).isTrue();
                    assertThat(message.isBodyAssignableTo(String.class)).isFalse();
                    Manifest manifest = message.getBody(Manifest.class);
                    assertThat(manifest.id()).isEqualTo(message.getJMSCorrelationID());
                    cleared.add(manifest);
                    last = message;
                }
                last.acknowledge();
                assertThat(consumer.receiveNoWait()).isNull();
            }
            assertThat(cleared).containsExactly(
                    new Manifest("MF-2001", "Rotterdam", 18_400L,
                            new ArrayList<>(List.of("CONT-001", "CONT-002"))),
                    hamburg);
            assertThat(cleared.get(0)).isNotSameAs(rotterdam);
            assertThat(cleared.get(0).containers()).doesNotContain("CONT-SMUGGLED");
        } finally {
            customs.close();
        }
    }

    @Test
    void defaultAllowlistAdmitsOnlyJavaLangUntilTheConnectionTrustsEverything() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        HashMap<String, Integer> tally = new HashMap<>(Map.of("Rotterdam", 2, "Hamburg", 1));
        Connection connection = factory.createConnection();
        try {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue tallies = session.createQueue(TALLIES);
            MessageProducer producer = session.createProducer(tallies);
            producer.send(session.createObjectMessage(18_400L));
            producer.send(session.createObjectMessage(tally));

            try (MessageConsumer consumer = session.createConsumer(tallies)) {
                ObjectMessage weight = (ObjectMessage) consumer.receive(10_000L);
                assertThat(weight).isNotNull();
                assertThat(weight.getBody(Long.class)).isEqualTo(18_400L);

                ObjectMessage tallyMessage = (ObjectMessage) consumer.receive(10_000L);
                assertThat(tallyMessage).isNotNull();
                assertThatThrownBy(tallyMessage::getObject)
                        .hasCauseInstanceOf(ClassNotFoundException.class)
                        .hasMessageContaining(HashMap.class.getName());
            }
        } finally {
            connection.close();
        }

        ActiveMQConnection trusting = (ActiveMQConnection) factory.createConnection();
        try {
            trusting.setTrustAllPackages(true);
            trusting.start();
            Session session = trusting.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue tallies = session.createQueue(TALLIES);
            MessageProducer producer = session.createProducer(tallies);
            producer.send(session.createObjectMessage(tally));
            producer.send(session.createObjectMessage(
                    new Manifest("MF-2004", "Gdansk", 3_100L, new ArrayList<>(List.of("CONT-9")))));
            try (MessageConsumer consumer = session.createConsumer(tallies)) {
                ObjectMessage tallyMessage = (ObjectMessage) consumer.receive(10_000L);
                assertThat(tallyMessage).isNotNull();
                assertThat(tallyMessage.getObject()).isInstanceOf(HashMap.class).isEqualTo(tally);

                ObjectMessage manifestMessage = (ObjectMessage) consumer.receive(10_000L);
                assertThat(manifestMessage).isNotNull();
                assertThat(manifestMessage.getObject()).isEqualTo(new Manifest(
                        "MF-2004", "Gdansk", 3_100L, new ArrayList<>(List.of("CONT-9"))));
            }
        } finally {
            trusting.close();
        }
    }

    @Test
    void dutyDeclarationsTravelAsOrderedTypedStreamFields() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        Connection connection = factory.createConnection();
        try {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue declarations = session.createQueue(DECLARATIONS);

            StreamMessage declaration = session.createStreamMessage();
            declaration.writeString("MF-2001");
            declaration.writeInt(42);
            declaration.writeDouble(1_250.75d);
            declaration.writeBoolean(true);
            declaration.writeBytes(new byte[] {0x0D, 0x0E, 0x0F});
            declaration.writeString("1400");
            declaration.setStringProperty("hsCode", "8471.30");
            session.createProducer(declarations).send(declaration);

            try (MessageConsumer consumer = session.createConsumer(declarations)) {
                StreamMessage received = (StreamMessage) consumer.receive(10_000L);
                assertThat(received).isNotNull();
                assertThat(received.getStringProperty("hsCode")).isEqualTo("8471.30");

                assertThat(received.readString()).isEqualTo("MF-2001");
                assertThat(received.readLong()).isEqualTo(42L);
                assertThat(received.readString()).isEqualTo("1250.75");
                assertThatThrownBy(received::readInt).isInstanceOf(MessageFormatException.class);
                assertThat(received.readBoolean()).isTrue();
                byte[] seal = new byte[8];
                assertThat(received.readBytes(seal)).isEqualTo(3);
                assertThat(seal).startsWith(0x0D, 0x0E, 0x0F);
                assertThat(received.readBytes(seal)).isEqualTo(-1);
                assertThat(received.readInt()).isEqualTo(1400);
                assertThatThrownBy(received::readObject).isInstanceOf(MessageEOFException.class);

                assertThatThrownBy(() -> received.writeString("amended"))
                        .isInstanceOf(MessageNotWriteableException.class);
                received.reset();
                assertThat(received.readObject()).isEqualTo("MF-2001");
                assertThat(received.readObject()).isEqualTo(42);

                received.clearBody();
                received.writeString("MF-2001-AMENDED");
                received.reset();
                assertThat(received.readString()).isEqualTo("MF-2001-AMENDED");
                assertThatThrownBy(received::readString).isInstanceOf(MessageEOFException.class);
            }
        } finally {
            connection.close();
        }
    }

    /// Lists the object messages still queued, in delivery order, without consuming them.
    private static List<ObjectMessage> browse(Session session, Queue queue) throws JMSException {
        List<ObjectMessage> queued = new ArrayList<>();
        try (QueueBrowser browser = session.createBrowser(queue)) {
            Enumeration<?> messages = browser.getEnumeration();
            while (messages.hasMoreElements()) {
                queued.add((ObjectMessage) messages.nextElement());
            }
        }
        return queued;
    }

    /// One freight manifest, carried as a Java-serialized object message body.
    static final class Manifest implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String id;

        private final String port;

        private final long grossKilograms;

        private final ArrayList<String> containers;

        Manifest(String id, String port, long grossKilograms, ArrayList<String> containers) {
            this.id = id;
            this.port = port;
            this.grossKilograms = grossKilograms;
            this.containers = containers;
        }

        String id() {
            return id;
        }

        ArrayList<String> containers() {
            return containers;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Manifest that
                    && grossKilograms == that.grossKilograms
                    && id.equals(that.id)
                    && port.equals(that.port)
                    && containers.equals(that.containers);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, port, grossKilograms, containers);
        }

        @Override
        public String toString() {
            return id + "@" + port + containers;
        }
    }
}
