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
import jakarta.jms.ResourceAllocationException;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQMessageProducer;
import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.AsyncCallback;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.activemq.broker.region.policy.VMPendingQueueMessageStoragePolicy;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Streams sensor telemetry through asynchronous producer sends with `AsyncCallback` completion
 * notifications, producer flow control against a full destination, and per-message
 * `INDIVIDUAL_ACKNOWLEDGE` consumption.
 *
 * A gateway fires readings with `useAsyncSend` and a producer window, learning of each
 * broker receipt through a callback; when a burst queue is out of memory the broker refuses
 * sends, which the gateway sees as `ResourceAllocationException` both on a callback and on a
 * synchronous persistent send. An analytics worker acknowledges only the alerts it handled,
 * so the skipped ones are redelivered to the next worker.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SensorTelemetryAsyncSendTest {

    private static final String READINGS = "telemetry.readings";

    private static final String BURST = "telemetry.burst";

    private static final String ALERTS = "telemetry.alerts";

    private static final long BURST_MEMORY_LIMIT = 64 * 1024;

    private final ObjectMapper mapper = new ObjectMapper();

    private BrokerService broker;

    private String brokerUrl;

    @BeforeAll
    void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("telemetry-broker");
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setUseShutdownHook(false);
        broker.getSystemUsage().setSendFailIfNoSpace(true);

        PolicyEntry burstPolicy = new PolicyEntry();
        burstPolicy.setQueue(BURST);
        burstPolicy.setProducerFlowControl(true);
        burstPolicy.setMemoryLimit(BURST_MEMORY_LIMIT);
        burstPolicy.setPendingQueuePolicy(new VMPendingQueueMessageStoragePolicy());
        PolicyMap policies = new PolicyMap();
        policies.setPolicyEntries(List.of(burstPolicy));
        broker.setDestinationPolicy(policies);

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
    void confirmsEachAsynchronousSendThroughItsCallback() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        factory.setUseAsyncSend(true);
        factory.setProducerWindowSize(16 * 1024);
        List<Reading> readings = List.of(
                new Reading("boiler-1", "temperature", 81.5),
                new Reading("boiler-1", "pressure", 2.4),
                new Reading("boiler-2", "temperature", 79.0),
                new Reading("chiller-1", "temperature", 6.5),
                new Reading("chiller-1", "flow", 12.25));

        Connection connection = factory.createConnection();
        try {
            ActiveMQConnection openWire = (ActiveMQConnection) connection;
            assertThat(openWire.isUseAsyncSend()).isTrue();
            assertThat(openWire.getProducerWindowSize()).isEqualTo(16 * 1024);
            connection.start();

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(READINGS);
            ActiveMQMessageProducer producer = (ActiveMQMessageProducer) session.createProducer(queue);
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

            ReceiptTracker tracker = new ReceiptTracker(readings.size());
            for (Reading reading : readings) {
                TextMessage message = session.createTextMessage(mapper.writeValueAsString(reading));
                message.setStringProperty("sensor", reading.sensor());
                producer.send(message, tracker.callbackFor(reading.sensor() + "/" + reading.metric()));
            }
            assertThat(tracker.await()).isTrue();
            assertThat(tracker.failures()).isEmpty();
            assertThat(tracker.confirmed()).containsExactlyInAnyOrder(
                    "boiler-1/temperature", "boiler-1/pressure", "boiler-2/temperature",
                    "chiller-1/temperature", "chiller-1/flow");

            MessageConsumer consumer = session.createConsumer(queue);
            List<Reading> received = new ArrayList<>();
            for (int i = 0; i < readings.size(); i++) {
                received.add(read(consumer.receive(5_000)));
            }
            assertThat(received).containsExactlyElementsOf(readings);
            assertThat(consumer.receiveNoWait()).isNull();
        } finally {
            connection.close();
        }
    }

    @Test
    void reportsBrokerRefusalWhenTheBurstQueueRunsOutOfMemory() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        Connection connection = factory.createConnection();
        try {
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue burst = session.createQueue(BURST);
            ActiveMQMessageProducer producer = (ActiveMQMessageProducer) session.createProducer(burst);
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            String payload = "x".repeat(8 * 1024);

            int accepted = 0;
            JMSException refusal = null;
            for (int i = 0; i < 64 && refusal == null; i++) {
                ReceiptTracker tracker = new ReceiptTracker(1);
                TextMessage frame = session.createTextMessage(payload);
                frame.setIntProperty("frame", i);
                producer.send(frame, tracker.callbackFor("frame-" + i));
                assertThat(tracker.await()).isTrue();
                if (tracker.failures().isEmpty()) {
                    accepted++;
                } else {
                    refusal = tracker.failures().get(0);
                }
            }
            assertThat(refusal).isInstanceOf(ResourceAllocationException.class);
            assertThat(refusal.getMessage()).contains("Usage Manager Memory Limit");
            assertThat(accepted).isPositive().isLessThan(64);

            MessageProducer blocking = session.createProducer(burst);
            blocking.setDeliveryMode(DeliveryMode.PERSISTENT);
            assertThatThrownBy(() -> blocking.send(session.createTextMessage(payload)))
                    .isInstanceOf(ResourceAllocationException.class);

            MessageConsumer drain = session.createConsumer(burst);
            for (int i = 0; i < accepted; i++) {
                Message frame = drain.receive(5_000);
                assertThat(frame).isNotNull();
                assertThat(frame.getIntProperty("frame")).isEqualTo(i);
            }
            assertThat(drain.receiveNoWait()).isNull();
            drain.close();

            ReceiptTracker recovered = new ReceiptTracker(1);
            producer.send(session.createTextMessage("after-drain"), recovered.callbackFor("after-drain"));
            assertThat(recovered.await()).isTrue();
            assertThat(recovered.failures()).isEmpty();
            assertThat(recovered.confirmed()).containsExactly("after-drain");
        } finally {
            connection.close();
        }
    }

    @Test
    void redeliversOnlyTheAlertsAWorkerDidNotAcknowledgeIndividually() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        Connection connection = factory.createConnection();
        try {
            connection.start();
            Session gateway = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue alerts = gateway.createQueue(ALERTS);
            MessageProducer producer = gateway.createProducer(alerts);
            for (int i = 1; i <= 4; i++) {
                TextMessage alert = gateway.createTextMessage("alert-" + i);
                alert.setBooleanProperty("critical", i % 2 == 0);
                producer.send(alert);
            }

            Session worker = connection.createSession(false, ActiveMQSession.INDIVIDUAL_ACKNOWLEDGE);
            assertThat(((ActiveMQSession) worker).isIndividualAcknowledge()).isTrue();
            assertThat(worker.getAcknowledgeMode()).isEqualTo(ActiveMQSession.INDIVIDUAL_ACKNOWLEDGE);
            MessageConsumer consumer = worker.createConsumer(alerts);
            List<String> handled = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                TextMessage alert = (TextMessage) consumer.receive(5_000);
                assertThat(alert).isNotNull();
                if (alert.getBooleanProperty("critical")) {
                    alert.acknowledge();
                    handled.add(alert.getText());
                }
            }
            assertThat(handled).containsExactly("alert-2", "alert-4");
            worker.close();

            Session nextWorker = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer retry = nextWorker.createConsumer(alerts);
            List<String> redelivered = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Message alert = retry.receive(5_000);
                assertThat(alert).isNotNull();
                redelivered.add(((TextMessage) alert).getText());
            }
            assertThat(redelivered).containsExactly("alert-1", "alert-3");
            assertThat(retry.receive(500)).isNull();
        } finally {
            connection.close();
        }
    }

    private Reading read(Message message) throws Exception {
        assertThat(message).isInstanceOf(TextMessage.class);
        Reading reading = mapper.readValue(((TextMessage) message).getText(), Reading.class);
        assertThat(message.getStringProperty("sensor")).isEqualTo(reading.sensor());
        return reading;
    }

    /// One sensor sample serialized as the JSON body of a telemetry message.
    record Reading(String sensor, String metric, double value) {
    }

    /// Collects broker receipts and refusals reported to `AsyncCallback`s.
    static final class ReceiptTracker {

        private final CountDownLatch completions;

        private final List<String> confirmed = Collections.synchronizedList(new ArrayList<>());

        private final List<JMSException> failures = Collections.synchronizedList(new ArrayList<>());

        ReceiptTracker(int expected) {
            completions = new CountDownLatch(expected);
        }

        AsyncCallback callbackFor(String key) {
            return new AsyncCallback() {
                @Override
                public void onSuccess() {
                    confirmed.add(key);
                    completions.countDown();
                }

                @Override
                public void onException(JMSException exception) {
                    failures.add(exception);
                    completions.countDown();
                }
            };
        }

        boolean await() throws InterruptedException {
            return completions.await(10, TimeUnit.SECONDS);
        }

        List<String> confirmed() {
            return List.copyOf(confirmed);
        }

        List<JMSException> failures() {
            return List.copyOf(failures);
        }
    }
}
