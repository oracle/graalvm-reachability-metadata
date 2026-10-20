/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import jakarta.jms.Connection;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.broker.region.Destination;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.transport.TransportListener;
import org.apache.activemq.transport.failover.FailoverTransport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Keeps a courier dispatch service connected through the failover transport: ordered
 * broker URIs, transport listeners observing interruption and resumption, consumer state
 * replayed onto the standby broker, startup reconnect limits, and send timeouts while
 * no broker is reachable.
 *
 * The dispatch desk connects with `failover:(primary,standby)?randomize=false`; when
 * the primary broker goes down the client reconnects to the standby on its own and the
 * courier keeps receiving jobs on the consumer it opened before the outage.
 */
class CourierDispatchFailoverTest {

    private static final String JOBS = "courier.jobs";

    private static final long WAIT_SECONDS = 30L;

    @Test
    void movesDispatchToStandbyBrokerWhenPrimaryStops() throws Exception {
        BrokerService primary = startBroker("dispatch-primary");
        BrokerService standby = startBroker("dispatch-standby");
        try {
            URI primaryUri = connectUri(primary);
            URI standbyUri = connectUri(standby);
            String url = "failover:(" + primaryUri + "," + standbyUri + ")"
                    + "?randomize=false&initialReconnectDelay=50&maxReconnectDelay=200"
                    + "&useExponentialBackOff=false";
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(url);
            LinkWatcher factoryWatcher = new LinkWatcher();
            factory.setTransportListener(factoryWatcher);
            List<JMSException> failures = new CopyOnWriteArrayList<>();
            factory.setExceptionListener(failures::add);

            Connection connection = factory.createConnection();
            try {
                connection.setClientID("dispatch-desk");
                connection.start();
                ActiveMQConnection openWire = (ActiveMQConnection) connection;
                LinkWatcher connectionWatcher = new LinkWatcher();
                openWire.addTransportListener(connectionWatcher);
                FailoverTransport failover = openWire.getTransport().narrow(FailoverTransport.class);
                assertThat(failover).isNotNull();
                assertThat(failover.isFaultTolerant()).isTrue();
                assertThat(failover.isRandomize()).isFalse();
                assertThat(failover.isConnected()).isTrue();
                assertThat(failover.getConnectedTransportURI()).isEqualTo(primaryUri);

                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Queue jobs = session.createQueue(JOBS);
                MessageProducer producer = session.createProducer(jobs);
                producer.setDeliveryMode(DeliveryMode.PERSISTENT);
                MessageConsumer courier = session.createConsumer(jobs);

                producer.send(job(session, "J-1", "Harbour Street 4"));
                assertThat(receiveJob(courier)).isEqualTo("J-1@Harbour Street 4");
                assertThat(enqueues(primary)).isEqualTo(1L);

                primary.stop();
                primary.waitUntilStopped();
                assertThat(factoryWatcher.interrupted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
                assertThat(factoryWatcher.resumed.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
                assertThat(connectionWatcher.interrupted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
                assertThat(connectionWatcher.resumed.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
                assertThat(failover.isConnected()).isTrue();
                assertThat(failover.getConnectedTransportURI()).isEqualTo(standbyUri);
                assertThat(factoryWatcher.sinceInterruption()).containsExactly("interrupted", "resumed");

                producer.send(job(session, "J-2", "Mill Lane 17"));
                producer.send(job(session, "J-3", "Quay Road 2"));
                assertThat(receiveJob(courier)).isEqualTo("J-2@Mill Lane 17");
                assertThat(receiveJob(courier)).isEqualTo("J-3@Quay Road 2");
                assertThat(enqueues(standby)).isEqualTo(2L);
                assertThat(standby.getDestination(new ActiveMQQueue(JOBS))
                        .getDestinationStatistics().getConsumers().getCount()).isEqualTo(1L);
                assertThat(connection.getClientID()).isEqualTo("dispatch-desk");
                assertThat(openWire.isTransportFailed()).isFalse();
                assertThat(failures).isEmpty();
            } finally {
                connection.close();
            }
            assertThat(((ActiveMQConnection) connection).isClosed()).isTrue();
        } finally {
            stopBroker(primary);
            stopBroker(standby);
        }
    }

    @Test
    void givesUpAfterStartupReconnectAttemptsWhenNoBrokerListens() throws Exception {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            closedPort = probe.getLocalPort();
        }
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(
                "failover:(tcp://127.0.0.1:" + closedPort + ")"
                        + "?startupMaxReconnectAttempts=2&initialReconnectDelay=10"
                        + "&useExponentialBackOff=false");

        assertThatThrownBy(() -> {
            Connection connection = factory.createConnection();
            try {
                connection.start();
            } finally {
                connection.close();
            }
        }).isInstanceOf(JMSException.class)
                .hasRootCauseInstanceOf(IOException.class);
    }

    @Test
    void failsSendAfterTimeoutWhileNoBrokerIsReachable() throws Exception {
        BrokerService only = startBroker("dispatch-single");
        try {
            URI onlyUri = connectUri(only);
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(
                    "failover:(" + onlyUri + ")?timeout=500&initialReconnectDelay=50"
                            + "&maxReconnectDelay=200&useExponentialBackOff=false");
            LinkWatcher watcher = new LinkWatcher();
            factory.setTransportListener(watcher);

            Connection connection = factory.createConnection();
            try {
                connection.start();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                MessageProducer producer = session.createProducer(session.createQueue(JOBS));
                producer.setDeliveryMode(DeliveryMode.PERSISTENT);
                producer.send(job(session, "J-10", "Station Square 1"));
                assertThat(enqueues(only)).isEqualTo(1L);

                only.stop();
                only.waitUntilStopped();
                assertThat(watcher.interrupted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

                assertThatThrownBy(() -> producer.send(job(session, "J-11", "Bridge End 9")))
                        .isInstanceOf(JMSException.class)
                        .hasMessageContaining("Failover timeout of 500 ms reached");
                FailoverTransport failover = ((ActiveMQConnection) connection).getTransport()
                        .narrow(FailoverTransport.class);
                assertThat(failover.isConnected()).isFalse();
                assertThat(watcher.sinceInterruption()).containsExactly("interrupted");
            } finally {
                connection.close();
            }
        } finally {
            stopBroker(only);
        }
    }

    private static BrokerService startBroker(String name) throws Exception {
        BrokerService broker = new BrokerService();
        broker.setBrokerName(name);
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setUseShutdownHook(false);
        broker.setAdvisorySupport(false);
        broker.addConnector("tcp://127.0.0.1:0");
        broker.start();
        assertThat(broker.waitUntilStarted()).isTrue();
        return broker;
    }

    private static URI connectUri(BrokerService broker) throws Exception {
        TransportConnector connector = broker.getTransportConnectors().get(0);
        return connector.getConnectUri();
    }

    private static void stopBroker(BrokerService broker) throws Exception {
        if (!broker.isStopped()) {
            broker.stop();
            broker.waitUntilStopped();
        }
    }

    /// Counts the jobs a broker accepted onto the dispatch queue.
    private static long enqueues(BrokerService broker) throws Exception {
        Destination destination = broker.getDestination(new ActiveMQQueue(JOBS));
        return destination.getDestinationStatistics().getEnqueues().getCount();
    }

    private static TextMessage job(Session session, String id, String address)
            throws JMSException {
        TextMessage message = session.createTextMessage(address);
        message.setJMSCorrelationID(id);
        return message;
    }

    private static String receiveJob(MessageConsumer courier) throws JMSException {
        TextMessage message = (TextMessage) courier.receive(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
        assertThat(message).isNotNull();
        return message.getJMSCorrelationID() + "@" + message.getText();
    }

    /// Records, in order, when the link to the broker drops and when failover restores it.
    static final class LinkWatcher implements TransportListener {

        final List<String> events = new CopyOnWriteArrayList<>();

        final CountDownLatch interrupted = new CountDownLatch(1);

        final CountDownLatch resumed = new CountDownLatch(1);

        @Override
        public void onCommand(Object command) {
        }

        @Override
        public void onException(IOException error) {
            events.add("failed");
        }

        @Override
        public void transportInterupted() {
            events.add("interrupted");
            interrupted.countDown();
        }

        @Override
        public void transportResumed() {
            events.add("resumed");
            if (interrupted.getCount() == 0) {
                resumed.countDown();
            }
        }

        /// The events seen since the first interruption, which drop any initial-connect resume.
        List<String> sinceInterruption() {
            List<String> seen = List.copyOf(events);
            int first = seen.indexOf("interrupted");
            return first < 0 ? List.of() : seen.subList(first, seen.size());
        }
    }
}
