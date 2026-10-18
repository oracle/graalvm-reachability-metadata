/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_jms_client;

import org.apache.activemq.artemis.api.jms.management.JMSManagementHelper;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueRequestor;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Operates a broker over the JMS management queue with `JMSManagementHelper`: attribute reads,
 * operation invocations with arguments, array and numeric results, and failed invocations.
 *
 * A billing operations console talks to the embedded broker only through JMS: it reads the
 * invoice backlog, counts and purges settled invoices by filter, pauses the queue for
 * maintenance while a worker is attached, resumes it, and sees an error for a missing queue.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvoiceQueueManagementTest {

    static {
        // Like the Artemis distribution, keep the broker audit log off: it is not the subject here.
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.activemq.audit", "warn");
    }

    private static final String INVOICES = "billing.invoices";

    private static final String INVOICES_RESOURCE = "queue." + INVOICES;

    /** The broker's default management address. */
    private static final String MANAGEMENT_ADDRESS = "activemq.management";

    private ActiveMQServer broker;

    private ActiveMQConnectionFactory connectionFactory;

    @BeforeAll
    void startBroker() throws Exception {
        String url = "tcp://127.0.0.1:" + freePort();
        Configuration configuration = new ConfigurationImpl()
                .setPersistenceEnabled(false)
                .setSecurityEnabled(false)
                .addAcceptorConfiguration("netty", url);
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
    void managesInvoiceBacklogThroughManagementQueue() throws Exception {
        try (QueueConnection connection = connectionFactory.createQueueConnection()) {
            connection.start();
            QueueSession session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue invoices = session.createQueue(INVOICES);
            try (MessageProducer billing = session.createProducer(invoices)) {
                billing.send(invoice(session, "INV-1", "paid"));
                billing.send(invoice(session, "INV-2", "overdue"));
                billing.send(invoice(session, "INV-3", "paid"));
                billing.send(invoice(session, "INV-4", "overdue"));
                billing.send(invoice(session, "INV-5", "paid"));
            }

            Console console = new Console(session);

            assertThat(console.attribute("broker", "queueNames", Object[].class)).contains(INVOICES);
            assertThat(console.attribute(INVOICES_RESOURCE, "address", String.class)).isEqualTo(INVOICES);
            assertThat(console.attribute(INVOICES_RESOURCE, "messageCount", Number.class).longValue())
                    .isEqualTo(5L);

            assertThat(console.invoke(INVOICES_RESOURCE, "countMessages", Number.class, "status = 'overdue'")
                    .longValue()).isEqualTo(2L);
            assertThat(console.invoke(INVOICES_RESOURCE, "removeMessages", Number.class, "status = 'paid'")
                    .intValue()).isEqualTo(3);
            assertThat(console.attribute(INVOICES_RESOURCE, "messageCount", Number.class).longValue())
                    .isEqualTo(2L);

            console.invoke(INVOICES_RESOURCE, "pause", Object.class);
            assertThat(console.attribute(INVOICES_RESOURCE, "paused", Boolean.class)).isTrue();

            try (MessageConsumer collections = session.createConsumer(invoices)) {
                assertThat(collections.receive(1_000)).isNull();
                assertThat(console.attribute(INVOICES_RESOURCE, "consumerCount", Number.class).intValue())
                        .isEqualTo(1);

                console.invoke(INVOICES_RESOURCE, "resume", Object.class);
                assertThat(console.attribute(INVOICES_RESOURCE, "paused", Boolean.class)).isFalse();

                List<String> chased = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    TextMessage message = (TextMessage) collections.receive(10_000);
                    assertThat(message).isNotNull();
                    assertThat(message.getStringProperty("status")).isEqualTo("overdue");
                    chased.add(message.getText());
                }
                assertThat(chased).containsExactly("INV-2", "INV-4");
                assertThat(collections.receiveNoWait()).isNull();
            }

            Message failure = console.request(request -> JMSManagementHelper.putOperationInvocation(
                    request, "queue.billing.archive", "pause"));
            assertThat(JMSManagementHelper.isOperationResult(failure)).isTrue();
            assertThat(JMSManagementHelper.hasOperationSucceeded(failure)).isFalse();
            assertThat(String.valueOf(JMSManagementHelper.getResult(failure)))
                    .contains("queue.billing.archive");

            console.close();
        }
    }

    private static TextMessage invoice(Session session, String id, String status) throws Exception {
        TextMessage message = session.createTextMessage(id);
        message.setStringProperty("status", status);
        return message;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** An operations console that sends management requests and checks their replies. */
    private static final class Console {

        private final QueueSession session;

        private final QueueRequestor requestor;

        Console(QueueSession session) throws Exception {
            this.session = session;
            this.requestor = new QueueRequestor(session, session.createQueue(MANAGEMENT_ADDRESS));
        }

        <T> T attribute(String resource, String attribute, Class<T> type) throws Exception {
            Message reply = request(request -> JMSManagementHelper.putAttribute(request, resource, attribute));
            // Every reply carries the outcome header, so the broker reports it as an operation result.
            assertThat(JMSManagementHelper.isOperationResult(reply)).isTrue();
            assertThat(JMSManagementHelper.hasOperationSucceeded(reply))
                    .as("reading %s of %s: %s", attribute, resource, JMSManagementHelper.getResult(reply))
                    .isTrue();
            return type.cast(JMSManagementHelper.getResult(reply));
        }

        <T> T invoke(String resource, String operation, Class<T> type, Object... arguments) throws Exception {
            Message reply = request(request -> JMSManagementHelper.putOperationInvocation(
                    request, resource, operation, arguments));
            assertThat(JMSManagementHelper.isOperationResult(reply)).isTrue();
            assertThat(JMSManagementHelper.hasOperationSucceeded(reply))
                    .as("invoking %s on %s: %s", operation, resource, JMSManagementHelper.getResult(reply))
                    .isTrue();
            return type.cast(JMSManagementHelper.getResult(reply));
        }

        Message request(RequestBuilder builder) throws Exception {
            Message request = session.createMessage();
            builder.build(request);
            Message reply = requestor.request(request);
            assertThat(reply).isNotNull();
            return reply;
        }

        void close() throws Exception {
            requestor.close();
        }
    }

    /** Fills a management request message. */
    @FunctionalInterface
    private interface RequestBuilder {
        void build(Message request) throws Exception;
    }
}
