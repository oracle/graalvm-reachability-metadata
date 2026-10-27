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
import org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs a request/reply quote service over JMS objects bootstrapped from the Artemis JNDI
 * provider (`ActiveMQInitialContextFactory`), answering on temporary reply queues inside
 * local transactions.
 *
 * A storefront looks up its connection factory and request queue by JNDI name, sends quote
 * requests carrying `JMSReplyTo` and `JMSCorrelationID`, and the pricing service consumes and
 * replies in one transacted session: a rolled-back attempt neither loses the request nor leaks
 * its reply, and the committed retry answers the caller exactly once.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuoteRequestReplyTest {

    static {
        // Like the Artemis distribution, keep the broker audit log off: it is not the subject here.
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.activemq.audit", "warn");
    }

    private static final Map<String, Long> PRICE_LIST_CENTS = Map.of("WIDGET", 1_250L, "GADGET", 4_999L);

    private ActiveMQServer broker;

    private InitialContext jndi;

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

        Properties environment = new Properties();
        environment.put(Context.INITIAL_CONTEXT_FACTORY, ActiveMQInitialContextFactory.class.getName());
        environment.put("connectionFactory.jms/QuoteConnectionFactory", url + "?callTimeout=10000&consumerWindowSize=0");
        environment.put("queue.jms/quoteRequests", "pricing.quote.requests");
        jndi = new InitialContext(environment);
    }

    @AfterAll
    void stopBroker() throws Exception {
        if (jndi != null) {
            jndi.close();
        }
        if (broker != null) {
            broker.stop();
        }
    }

    @Test
    void answersQuoteRequestsOnTemporaryQueuesWithTransactedRetries() throws Exception {
        ConnectionFactory connectionFactory = (ConnectionFactory) jndi.lookup("jms/QuoteConnectionFactory");
        Queue requests = (Queue) jndi.lookup("jms/quoteRequests");
        assertThat(connectionFactory).isInstanceOf(ActiveMQConnectionFactory.class);
        assertThat(((ActiveMQConnectionFactory) connectionFactory).getCallTimeout()).isEqualTo(10_000L);
        assertThat(((ActiveMQConnectionFactory) connectionFactory).getConsumerWindowSize()).isZero();
        assertThat(requests.getQueueName()).isEqualTo("pricing.quote.requests");
        Queue auditTrail = (Queue) jndi.lookup("dynamicQueues/pricing.audit");
        assertThat(auditTrail.getQueueName()).isEqualTo("pricing.audit");

        try (Connection storefront = connectionFactory.createConnection();
             Connection pricing = connectionFactory.createConnection()) {
            Session storefrontSession = storefront.createSession(false, Session.AUTO_ACKNOWLEDGE);
            TemporaryQueue replies = storefrontSession.createTemporaryQueue();
            MessageConsumer replyConsumer = storefrontSession.createConsumer(replies);
            storefront.start();

            MessageProducer requester = storefrontSession.createProducer(requests);
            requester.send(request(storefrontSession, replies, "quote-1", "WIDGET", 4));
            requester.send(request(storefrontSession, replies, "quote-2", "GADGET", 2));

            Session pricingSession = pricing.createSession(true, Session.SESSION_TRANSACTED);
            assertThat(pricingSession.getTransacted()).isTrue();
            MessageConsumer incoming = pricingSession.createConsumer(requests);
            MessageProducer responder = pricingSession.createProducer(null);
            MessageProducer auditor = pricingSession.createProducer(auditTrail);
            pricing.start();

            Message firstAttempt = incoming.receive(10_000);
            assertThat(firstAttempt.getJMSCorrelationID()).isEqualTo("quote-1");
            assertThat(firstAttempt.getJMSRedelivered()).isFalse();
            answer(pricingSession, responder, auditor, firstAttempt);
            pricingSession.rollback();
            assertThat(replyConsumer.receiveNoWait()).isNull();

            Map<String, Message> handled = new LinkedHashMap<>();
            for (int i = 0; i < 2; i++) {
                Message request = incoming.receive(10_000);
                assertThat(request).isNotNull();
                handled.put(request.getJMSCorrelationID(), request);
                answer(pricingSession, responder, auditor, request);
                pricingSession.commit();
            }
            assertThat(handled.keySet()).containsExactly("quote-1", "quote-2");
            Message retried = handled.get("quote-1");
            assertThat(retried.getJMSRedelivered()).isTrue();
            assertThat(retried.getIntProperty("JMSXDeliveryCount")).isEqualTo(2);
            assertThat(handled.get("quote-2").getJMSRedelivered()).isFalse();
            assertThat(incoming.receiveNoWait()).isNull();

            Map<String, Quote> quotes = new LinkedHashMap<>();
            for (int i = 0; i < 2; i++) {
                Message reply = replyConsumer.receive(10_000);
                assertThat(reply).isNotNull();
                assertThat(reply.getJMSDestination()).isEqualTo(replies);
                quotes.put(reply.getJMSCorrelationID(), readQuote(reply));
            }
            assertThat(quotes).containsExactly(
                    Map.entry("quote-1", new Quote("WIDGET", 4, 5_000L)),
                    Map.entry("quote-2", new Quote("GADGET", 2, 9_998L)));
            assertThat(replyConsumer.receiveNoWait()).isNull();

            Session auditSession = pricing.createSession(false, Session.AUTO_ACKNOWLEDGE);
            try (MessageConsumer audit = auditSession.createConsumer(auditTrail)) {
                assertThat(((TextMessage) audit.receive(10_000)).getText()).isEqualTo("quote-1 WIDGET x4");
                assertThat(((TextMessage) audit.receive(10_000)).getText()).isEqualTo("quote-2 GADGET x2");
                assertThat(audit.receiveNoWait()).isNull();
            }

            replyConsumer.close();
            replies.delete();
        }
    }

    private static TextMessage request(Session session, TemporaryQueue replies, String correlationId,
                                       String sku, int quantity) throws JMSException {
        TextMessage message = session.createTextMessage(sku);
        message.setIntProperty("quantity", quantity);
        message.setJMSCorrelationID(correlationId);
        message.setJMSReplyTo(replies);
        return message;
    }

    /** Prices one request and stages its reply and audit entry in the current transaction. */
    private static void answer(Session session, MessageProducer responder, MessageProducer auditor,
                               Message request) throws JMSException {
        String sku = ((TextMessage) request).getText();
        int quantity = request.getIntProperty("quantity");
        BytesMessage reply = session.createBytesMessage();
        reply.writeUTF(sku);
        reply.writeInt(quantity);
        reply.writeLong(PRICE_LIST_CENTS.get(sku) * quantity);
        reply.setJMSCorrelationID(request.getJMSCorrelationID());
        responder.send(request.getJMSReplyTo(), reply);
        auditor.send(session.createTextMessage(request.getJMSCorrelationID() + " " + sku + " x" + quantity));
    }

    private static Quote readQuote(Message message) throws JMSException {
        assertThat(message).isInstanceOf(BytesMessage.class);
        BytesMessage bytes = (BytesMessage) message;
        return new Quote(bytes.readUTF(), bytes.readInt(), bytes.readLong());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** A priced quote as the pricing service returns it. */
    record Quote(String sku, int quantity, long totalCents) {
    }
}
