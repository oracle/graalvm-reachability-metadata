/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_jms_client;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
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
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Routes fleet telemetry through the Artemis address model as JMS destinations name it:
 * wildcard topic subscriptions (`#` and `*`), fully qualified queue names (`address::queue`)
 * on a multicast address, and queue attributes carried by parameterised destination names
 * (`?last-value-key=` and `?exclusive=true`).
 *
 * Trucks publish to per-region topics; the operations desk subscribes with wildcards, billing and
 * audit read their own queue of a shared dispatch address, a tracking board keeps only each
 * truck's latest position, and a single maintenance worker owns its queue until it leaves.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FleetTelemetryAddressingTest {

    static {
        // Like the Artemis distribution, keep the broker audit log off: it is not the subject here.
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.activemq.audit", "warn");
    }

    private static final String DISPATCH = "fleet.dispatch";

    private static final long RECEIVE_TIMEOUT = 10_000;

    private ActiveMQServer broker;

    private ActiveMQConnectionFactory connectionFactory;

    @BeforeAll
    void startBroker() throws Exception {
        String url = "tcp://127.0.0.1:" + freePort();
        CoreAddressConfiguration dispatch = new CoreAddressConfiguration()
                .setName(DISPATCH)
                .addRoutingType(RoutingType.MULTICAST)
                .addQueueConfig(new QueueConfiguration("billing").setAddress(DISPATCH)
                        .setRoutingType(RoutingType.MULTICAST))
                .addQueueConfig(new QueueConfiguration("audit").setAddress(DISPATCH)
                        .setRoutingType(RoutingType.MULTICAST));
        Configuration configuration = new ConfigurationImpl()
                .setPersistenceEnabled(false)
                .setSecurityEnabled(false)
                .addAcceptorConfiguration("netty", url)
                .addAddressConfiguration(dispatch);
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
    void wildcardSubscriptionsMatchRegionalTopics() throws Exception {
        try (JMSContext desk = connectionFactory.createContext();
             JMSContext trucks = connectionFactory.createContext()) {
            JMSConsumer everything = desk.createConsumer(desk.createTopic("fleet.telemetry.#"));
            JMSConsumer allAlerts = desk.createConsumer(desk.createTopic("fleet.telemetry.*.alerts"));
            JMSConsumer europe = desk.createConsumer(desk.createTopic("fleet.telemetry.eu.*"));

            JMSProducer producer = trucks.createProducer();
            producer.send(trucks.createTopic("fleet.telemetry.eu.alerts"), "T1 brake pressure low");
            producer.send(trucks.createTopic("fleet.telemetry.us.alerts"), "T7 tyre puncture");
            producer.send(trucks.createTopic("fleet.telemetry.eu.positions"), "T1 48.85,2.35");
            producer.send(trucks.createTopic("fleet.telemetry.us.positions.raw"), "T7 40.71,-74.00");

            assertThat(drain(everything, 4)).containsExactly(
                    "fleet.telemetry.eu.alerts|T1 brake pressure low",
                    "fleet.telemetry.us.alerts|T7 tyre puncture",
                    "fleet.telemetry.eu.positions|T1 48.85,2.35",
                    "fleet.telemetry.us.positions.raw|T7 40.71,-74.00");
            assertThat(drain(allAlerts, 2)).containsExactly(
                    "fleet.telemetry.eu.alerts|T1 brake pressure low",
                    "fleet.telemetry.us.alerts|T7 tyre puncture");
            assertThat(drain(europe, 2)).containsExactly(
                    "fleet.telemetry.eu.alerts|T1 brake pressure low",
                    "fleet.telemetry.eu.positions|T1 48.85,2.35");
            assertThat(everything.receiveNoWait()).isNull();
            assertThat(allAlerts.receiveNoWait()).isNull();
            assertThat(europe.receiveNoWait()).isNull();
        }
    }

    @Test
    void fullyQualifiedQueueNamesReadOneQueueOfAnAddress() throws Exception {
        try (JMSContext dispatcher = connectionFactory.createContext();
             JMSContext billing = connectionFactory.createContext();
             JMSContext audit = connectionFactory.createContext()) {
            JMSProducer producer = dispatcher.createProducer();
            producer.send(dispatcher.createTopic(DISPATCH), "job-1 Lyon->Milan");
            producer.send(dispatcher.createTopic(DISPATCH), "job-2 Porto->Madrid");
            producer.send(dispatcher.createTopic(DISPATCH + "::audit"), "job-2 rerouted via Valladolid");

            Topic billingQueue = billing.createTopic(DISPATCH + "::billing");
            assertThat(billingQueue.getTopicName()).isEqualTo(DISPATCH + "::billing");
            try (JMSConsumer consumer = billing.createConsumer(billingQueue)) {
                assertThat(drain(consumer, 2)).containsExactly(
                        DISPATCH + "|job-1 Lyon->Milan",
                        DISPATCH + "|job-2 Porto->Madrid");
                assertThat(consumer.receiveNoWait()).isNull();
            }

            try (JMSConsumer consumer = audit.createConsumer(audit.createTopic(DISPATCH + "::audit"))) {
                List<String> trail = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    trail.add(consumer.receiveBody(String.class, RECEIVE_TIMEOUT));
                }
                assertThat(trail).containsExactly(
                        "job-1 Lyon->Milan", "job-2 Porto->Madrid", "job-2 rerouted via Valladolid");
                assertThat(consumer.receiveNoWait()).isNull();
            }
        }
    }

    @Test
    void lastValueKeyInTheQueueNameKeepsTheLatestPositionPerTruck() throws Exception {
        String positions = "fleet.positions?last-value-key=truck";
        try (JMSContext trucks = connectionFactory.createContext();
             JMSContext board = connectionFactory.createContext()) {
            Queue queue = trucks.createQueue(positions);
            assertThat(queue.getQueueName()).isEqualTo("fleet.positions");
            JMSProducer producer = trucks.createProducer();
            sendPosition(producer, queue, "T1", "48.85,2.35");
            sendPosition(producer, queue, "T2", "41.15,-8.61");
            sendPosition(producer, queue, "T1", "48.90,2.40");
            sendPosition(producer, queue, "T1", "49.00,2.55");
            sendPosition(producer, queue, "T2", "41.20,-8.50");

            Queue latest = board.createQueue(positions);
            assertThat(browse(board, latest)).containsExactlyInAnyOrder("T1@49.00,2.55", "T2@41.20,-8.50");
            try (JMSConsumer consumer = board.createConsumer(latest)) {
                List<String> onBoard = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    Message message = consumer.receive(RECEIVE_TIMEOUT);
                    assertThat(message).isNotNull();
                    onBoard.add(message.getStringProperty("truck") + "@" + message.getBody(String.class));
                }
                assertThat(onBoard).containsExactlyInAnyOrder("T1@49.00,2.55", "T2@41.20,-8.50");
                assertThat(consumer.receiveNoWait()).isNull();
            }
        }
    }

    @Test
    void exclusiveQueueDeliversToOneWorkerUntilItLeaves() throws Exception {
        String maintenance = "fleet.maintenance?exclusive=true";
        try (JMSContext firstShift = connectionFactory.createContext();
             JMSContext secondShift = connectionFactory.createContext();
             JMSContext workshop = connectionFactory.createContext()) {
            // The workshop's first job creates the queue, so the broker takes its attributes from the name.
            Queue queue = workshop.createQueue(maintenance);
            JMSProducer producer = workshop.createProducer();
            List<String> jobs = List.of("T1 oil change", "T2 brake pads", "T7 tyre swap", "T3 inspection");
            producer.send(queue, jobs.get(0));
            JMSConsumer owner = firstShift.createConsumer(firstShift.createQueue(maintenance));
            JMSConsumer standby = secondShift.createConsumer(secondShift.createQueue(maintenance));
            for (String job : jobs.subList(1, jobs.size())) {
                producer.send(queue, job);
            }

            List<String> handled = new ArrayList<>();
            for (int i = 0; i < jobs.size(); i++) {
                handled.add(owner.receiveBody(String.class, RECEIVE_TIMEOUT));
            }
            assertThat(handled).containsExactlyElementsOf(jobs);
            assertThat(standby.receiveNoWait()).isNull();

            owner.close();
            producer.send(queue, "T9 windscreen");
            assertThat(standby.receiveBody(String.class, RECEIVE_TIMEOUT)).isEqualTo("T9 windscreen");
            standby.close();
        }
    }

    private static void sendPosition(JMSProducer producer, Queue queue, String truck, String position) {
        producer.setProperty("truck", truck).send(queue, position);
    }

    private static List<String> drain(JMSConsumer consumer, int count) throws Exception {
        List<String> received = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Message message = consumer.receive(RECEIVE_TIMEOUT);
            assertThat(message).isInstanceOf(TextMessage.class);
            Topic origin = (Topic) message.getJMSDestination();
            received.add(origin.getTopicName() + "|" + ((TextMessage) message).getText());
        }
        return received;
    }

    private static List<String> browse(JMSContext context, Queue queue) throws Exception {
        List<String> seen = new ArrayList<>();
        try (QueueBrowser browser = context.createBrowser(queue)) {
            Enumeration<?> messages = browser.getEnumeration();
            for (Object element : Collections.list(messages)) {
                Message message = (Message) element;
                seen.add(message.getStringProperty("truck") + "@" + message.getBody(String.class));
            }
        }
        return seen;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
