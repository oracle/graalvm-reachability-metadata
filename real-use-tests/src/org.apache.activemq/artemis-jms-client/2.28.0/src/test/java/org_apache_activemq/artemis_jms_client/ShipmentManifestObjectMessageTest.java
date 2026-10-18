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

import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageFormatException;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exchanges Java-serialized `ObjectMessage` payloads guarded by the Artemis client's
 * deserialization allow and deny lists.
 *
 * A shipping service sends serializable manifests and a stock summary over a queue. The
 * carrier gateway trusts the application package and the JDK collection and number types,
 * so it reads manifests through `getObject`, `getBody` and `receiveBody`. An audit tool
 * whose factory denies the application package, and a dashboard that allows only JDK
 * types, receive the same messages but are refused the manifest while still reading the
 * plain JDK stock summary. The lists are set through factory setters and connector URL
 * parameters.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ShipmentManifestObjectMessageTest {

    static {
        // Like the Artemis distribution, keep the broker audit log off: it is not the subject here.
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.activemq.audit", "warn");
    }

    private static final String MANIFESTS = "shipping.manifests";

    private static final String STOCK = "shipping.stock";

    private static final String APPLICATION_PACKAGE = Manifest.class.getPackageName();

    private ActiveMQServer broker;

    private String url;

    @BeforeAll
    void startBroker() throws Exception {
        url = "tcp://127.0.0.1:" + freePort();
        Configuration configuration = new ConfigurationImpl()
                .setPersistenceEnabled(false)
                .setSecurityEnabled(false)
                .addAcceptorConfiguration("netty", url);
        broker = ActiveMQServers.newActiveMQServer(configuration);
        broker.start();
        assertThat(broker.waitForActivation(30, TimeUnit.SECONDS)).isTrue();
    }

    @AfterAll
    void stopBroker() throws Exception {
        if (broker != null) {
            broker.stop();
        }
    }

    @Test
    void trustedGatewayReadsManifestsThroughEveryBodyAccessor() throws Exception {
        Manifest manifest = manifest("M-100", "Rotterdam");
        Manifest express = manifest("M-101", "Hamburg");
        try (ActiveMQConnectionFactory shipping = new ActiveMQConnectionFactory(url);
                ActiveMQConnectionFactory gateway = trustingFactory()) {
            String queue = MANIFESTS + ".gateway";
            send(shipping, queue, manifest);
            send(shipping, queue, express);
            sendEmpty(shipping, queue);

            try (JMSContext context = gateway.createContext(JMSContext.CLIENT_ACKNOWLEDGE)) {
                JMSConsumer consumer = context.createConsumer(context.createQueue(queue));

                Message received = consumer.receive(10_000);
                assertThat(received).isInstanceOf(ObjectMessage.class);
                assertThat(received.getStringProperty("destination")).isEqualTo("Rotterdam");
                assertThat(((ObjectMessage) received).getObject()).isEqualTo(manifest);
                assertThat(received.isBodyAssignableTo(Manifest.class)).isTrue();
                assertThat(received.isBodyAssignableTo(String.class)).isFalse();
                Manifest body = received.getBody(Manifest.class);
                assertThat(body.totalWeight()).isEqualByComparingTo("31.25");
                assertThat(body.parcels()).extracting(Parcel::sku).containsExactly("SKU-1", "SKU-2");
                received.acknowledge();

                assertThat(consumer.receiveBody(Manifest.class, 10_000)).isEqualTo(express);

                Message empty = consumer.receive(10_000);
                assertThat(empty).isInstanceOf(ObjectMessage.class);
                assertThat(((ObjectMessage) empty).getObject()).isNull();
                assertThat(empty.getBody(Manifest.class)).isNull();
                empty.acknowledge();
            }
        }
    }

    @Test
    void denyListRejectsApplicationTypesButKeepsJdkPayloadsReadable() throws Exception {
        try (ActiveMQConnectionFactory shipping = new ActiveMQConnectionFactory(url);
                ActiveMQConnectionFactory audit = new ActiveMQConnectionFactory(
                        url + "?deserializationWhiteList=*&deserializationBlackList=" + APPLICATION_PACKAGE)) {
            assertThat(audit.getDeserializationBlackList()).isEqualTo(APPLICATION_PACKAGE);
            assertThat(audit.getDeserializationWhiteList()).isEqualTo("*");
            String manifests = MANIFESTS + ".audit";
            String stock = STOCK + ".audit";
            send(shipping, manifests, manifest("M-200", "Antwerp"));
            send(shipping, stock, stockSummary());

            assertManifestRefused(audit, manifests);
            assertThat(readStock(audit, stock)).isEqualTo(stockSummary());
        }
    }

    @Test
    void allowListLimitedToJdkTypesRefusesApplicationManifests() throws Exception {
        try (ActiveMQConnectionFactory shipping = new ActiveMQConnectionFactory(url);
                ActiveMQConnectionFactory dashboard = new ActiveMQConnectionFactory(url)) {
            dashboard.setDeserializationWhiteList("java.lang,java.util");
            String manifests = MANIFESTS + ".dashboard";
            String stock = STOCK + ".dashboard";
            send(shipping, manifests, manifest("M-300", "Gdansk"));
            send(shipping, stock, stockSummary());

            assertManifestRefused(dashboard, manifests);
            assertThat(readStock(dashboard, stock)).isEqualTo(stockSummary());
        }
    }

    private ActiveMQConnectionFactory trustingFactory() {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(url);
        factory.setDeserializationWhiteList(String.join(",", "java.lang", "java.math", "java.util", APPLICATION_PACKAGE));
        return factory;
    }

    private static void assertManifestRefused(ActiveMQConnectionFactory factory, String queue) throws JMSException {
        try (JMSContext context = factory.createContext(JMSContext.CLIENT_ACKNOWLEDGE)) {
            JMSConsumer consumer = context.createConsumer(context.createQueue(queue));
            Message received = consumer.receive(10_000);
            assertThat(received).isInstanceOf(ObjectMessage.class);
            assertThat(received.getStringProperty("manifestId")).startsWith("M-");
            assertThatThrownBy(((ObjectMessage) received)::getObject)
                    .isInstanceOf(JMSException.class)
                    .hasMessageContaining(Manifest.class.getName());
            assertThatThrownBy(() -> received.getBody(Manifest.class))
                    .isInstanceOf(MessageFormatException.class);
            received.acknowledge();
        }
    }

    private static Map<String, Integer> readStock(ActiveMQConnectionFactory factory, String queue) throws JMSException {
        try (JMSContext context = factory.createContext()) {
            JMSConsumer consumer = context.createConsumer(context.createQueue(queue));
            ObjectMessage received = (ObjectMessage) consumer.receive(10_000);
            assertThat(received).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Integer> stock = (Map<String, Integer>) received.getObject();
            return stock;
        }
    }

    private static void send(ActiveMQConnectionFactory factory, String queueName, Manifest manifest) {
        try (JMSContext context = factory.createContext()) {
            Queue queue = context.createQueue(queueName);
            ObjectMessage message = context.createObjectMessage(manifest);
            context.createProducer()
                    .setProperty("manifestId", manifest.id())
                    .setProperty("destination", manifest.destination())
                    .send(queue, message);
        }
    }

    private static void send(ActiveMQConnectionFactory factory, String queueName, HashMap<String, Integer> stock) {
        try (JMSContext context = factory.createContext()) {
            context.createProducer().send(context.createQueue(queueName), context.createObjectMessage(stock));
        }
    }

    private static void sendEmpty(ActiveMQConnectionFactory factory, String queueName) {
        try (JMSContext context = factory.createContext()) {
            context.createProducer().send(context.createQueue(queueName), context.createObjectMessage());
        }
    }

    private static Manifest manifest(String id, String destination) {
        List<Parcel> parcels = new ArrayList<>();
        parcels.add(new Parcel("SKU-1", 3, new BigDecimal("12.50")));
        parcels.add(new Parcel("SKU-2", 1, new BigDecimal("18.75")));
        return new Manifest(id, destination, parcels);
    }

    private static HashMap<String, Integer> stockSummary() {
        HashMap<String, Integer> stock = new HashMap<>();
        stock.put("SKU-1", 120);
        stock.put("SKU-2", 45);
        return stock;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /// A parcel on a manifest: stock-keeping unit, quantity and weight in kilograms.
    record Parcel(String sku, int quantity, BigDecimal weight) implements Serializable {
    }

    /// A shipment manifest an application serializes into an `ObjectMessage`.
    record Manifest(String id, String destination, List<Parcel> parcels) implements Serializable {

        BigDecimal totalWeight() {
            return parcels.stream().map(Parcel::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}
