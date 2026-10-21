/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_jms_client;

import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.FailoverEventListener;
import org.apache.activemq.artemis.api.core.client.FailoverEventType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.jms.client.ActiveMQConnection;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reconnects a JMS client automatically across a broker restart through the connector URL's
 * `reconnectAttempts` and `retryInterval` parameters, a `FailoverEventListener`, and blocking
 * acknowledgements.
 *
 * A job worker stays connected to a persistent embedded broker while operations restart it:
 * the client reports the failure and the completed reconnection, its session and consumer keep
 * working on the restarted broker, the unacknowledged durable jobs are delivered again, the
 * acknowledged one is not, and the JMS exception listener is told once that the broker shut down.
 */
class JobDispatchReconnectionTest {

    static {
        // Like the Artemis distribution, keep the broker audit log off: it is not the subject here.
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.activemq.audit", "warn");
    }

    private static final String JOBS = "dispatch.jobs";

    @TempDir
    Path dataDirectory;

    private ActiveMQServer broker;

    @AfterEach
    void stopBroker() throws Exception {
        if (broker != null) {
            broker.stop();
        }
    }

    @Test
    void workerKeepsConsumingAcrossBrokerRestart() throws Exception {
        String acceptorUrl = "tcp://127.0.0.1:" + freePort();
        broker = startBroker(acceptorUrl);

        String clientUrl = acceptorUrl
                + "?reconnectAttempts=-1&retryInterval=100&retryIntervalMultiplier=1.0"
                + "&blockOnAcknowledge=true&blockOnDurableSend=true";
        FailoverLog failoverLog = new FailoverLog();
        List<JMSException> connectionErrors = new CopyOnWriteArrayList<>();
        CountDownLatch connectionErrorReported = new CountDownLatch(1);

        try (ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(clientUrl);
                Connection connection = connectionFactory.createConnection()) {
            ((ActiveMQConnection) connection).setFailoverListener(failoverLog);
            connection.setExceptionListener(error -> {
                connectionErrors.add(error);
                connectionErrorReported.countDown();
            });
            connection.start();

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue jobs = session.createQueue(JOBS);
            MessageProducer scheduler = session.createProducer(jobs);
            scheduler.setDeliveryMode(DeliveryMode.PERSISTENT);
            MessageConsumer worker = session.createConsumer(jobs);

            scheduler.send(job(session, "rebuild-index"));
            scheduler.send(job(session, "rotate-logs"));
            scheduler.send(job(session, "send-digest"));

            TextMessage first = (TextMessage) worker.receive(10_000);
            assertThat(first).isNotNull();
            assertThat(first.getText()).isEqualTo("rebuild-index");

            broker.stop();
            assertThat(failoverLog.failureDetected.await(30, TimeUnit.SECONDS)).isTrue();

            broker = startBroker(acceptorUrl);
            assertThat(failoverLog.failoverCompleted.await(30, TimeUnit.SECONDS)).isTrue();
            assertThat(failoverLog.events)
                    .containsExactly(FailoverEventType.FAILURE_DETECTED, FailoverEventType.FAILOVER_COMPLETED);

            List<String> resumed = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                TextMessage message = (TextMessage) worker.receive(10_000);
                assertThat(message).isNotNull();
                resumed.add(message.getText());
            }
            assertThat(resumed).containsExactly("rotate-logs", "send-digest");

            scheduler.send(job(session, "purge-cache"));
            TextMessage after = (TextMessage) worker.receive(10_000);
            assertThat(after).isNotNull();
            assertThat(after.getText()).isEqualTo("purge-cache");
            assertThat(after.getStringProperty("kind")).isEqualTo("maintenance");
            assertThat(worker.receiveNoWait()).isNull();

            assertThat(broker.locateQueue(JOBS).getMessageCount()).isZero();
            assertThat(connectionErrorReported.await(30, TimeUnit.SECONDS)).isTrue();
            assertThat(connectionErrors).singleElement().satisfies(error -> assertThat(error.getMessage())
                    .contains(ActiveMQExceptionType.DISCONNECTED.name(), "server shutdown"));
        }
    }

    private ActiveMQServer startBroker(String acceptorUrl) throws Exception {
        Configuration configuration = new ConfigurationImpl()
                .setPersistenceEnabled(true)
                .setSecurityEnabled(false)
                .setJournalType(JournalType.NIO)
                .setJournalDirectory(dataDirectory.resolve("journal").toString())
                .setBindingsDirectory(dataDirectory.resolve("bindings").toString())
                .setPagingDirectory(dataDirectory.resolve("paging").toString())
                .setLargeMessagesDirectory(dataDirectory.resolve("large-messages").toString())
                .addAcceptorConfiguration("netty", acceptorUrl)
                .addQueueConfiguration(new QueueConfiguration(JOBS).setRoutingType(RoutingType.ANYCAST));
        ActiveMQServer server = ActiveMQServers.newActiveMQServer(configuration);
        server.start();
        assertThat(server.waitForActivation(30, TimeUnit.SECONDS)).isTrue();
        return server;
    }

    private static TextMessage job(Session session, String name) throws JMSException {
        TextMessage message = session.createTextMessage(name);
        message.setStringProperty("kind", "maintenance");
        return message;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** Records the client's failover events and signals the failure and the reconnection. */
    private static final class FailoverLog implements FailoverEventListener {

        private final List<FailoverEventType> events = new CopyOnWriteArrayList<>();

        private final CountDownLatch failureDetected = new CountDownLatch(1);

        private final CountDownLatch failoverCompleted = new CountDownLatch(1);

        @Override
        public void failoverEvent(FailoverEventType eventType) {
            events.add(eventType);
            if (eventType == FailoverEventType.FAILURE_DETECTED) {
                failureDetected.countDown();
            } else if (eventType == FailoverEventType.FAILOVER_COMPLETED) {
                failoverCompleted.countDown();
            }
        }
    }
}
