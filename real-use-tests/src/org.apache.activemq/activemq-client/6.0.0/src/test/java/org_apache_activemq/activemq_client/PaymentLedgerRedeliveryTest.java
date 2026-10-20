/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Connection;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.RedeliveryPolicy;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.RedeliveryPolicyMap;
import org.apache.activemq.command.ActiveMQQueue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Posts payments through transacted sessions with a per-destination redelivery policy and
 * dead-letter routing: producer rollback and commit, consumer rollback-driven redelivery,
 * delivery counting, and the poison acknowledgement that moves a message to the DLQ.
 *
 * A checkout service sends payments in a transaction and abandons a draft batch; the
 * ledger consumes each payment in its own transaction, rolls back the one it cannot post
 * until the client gives up on it, and an operator then finds it on the dead-letter queue.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentLedgerRedeliveryTest {

    private static final String PAYMENTS = "payments.ledger";

    private static final String DEAD_LETTERS = "ActiveMQ.DLQ";

    private static final int MAXIMUM_REDELIVERIES = 2;

    private final ObjectMapper mapper = new ObjectMapper();

    private BrokerService broker;

    @BeforeAll
    void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("ledger-broker");
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setUseShutdownHook(false);
        broker.start();
        assertThat(broker.waitUntilStarted()).isTrue();
    }

    @AfterAll
    void stopBroker() throws Exception {
        broker.stop();
        broker.waitUntilStopped();
    }

    @Test
    void retriesUnpostablePaymentUntilItIsDeadLettered() throws Exception {
        ActiveMQConnectionFactory factory =
                new ActiveMQConnectionFactory("vm://ledger-broker?create=false");
        factory.setRedeliveryPolicyMap(redeliveryPolicies());
        assertThat(factory.getRedeliveryPolicyMap().getEntryFor(new ActiveMQQueue(PAYMENTS))
                .getMaximumRedeliveries()).isEqualTo(MAXIMUM_REDELIVERIES);

        Connection connection = factory.createConnection();
        try {
            connection.start();
            Session checkout = connection.createSession(true, Session.SESSION_TRANSACTED);
            assertThat(checkout.getTransacted()).isTrue();
            Queue payments = checkout.createQueue(PAYMENTS);
            MessageProducer producer = checkout.createProducer(payments);

            send(checkout, producer, new Payment("DRAFT-1", "acct-9", 500));
            checkout.rollback();
            assertThat(browse(checkout, payments)).isEmpty();

            send(checkout, producer, new Payment("PAY-1", "acct-1", 1_250));
            send(checkout, producer, new Payment("PAY-2", "acct-2", -75));
            send(checkout, producer, new Payment("PAY-3", "acct-3", 4_000));
            checkout.commit();
            assertThat(browse(checkout, payments)).containsExactly("PAY-1", "PAY-2", "PAY-3");

            Session ledger = connection.createSession(true, Session.SESSION_TRANSACTED);
            List<Delivery> deliveries = new ArrayList<>();
            List<String> posted = new ArrayList<>();
            try (MessageConsumer consumer = ledger.createConsumer(ledger.createQueue(PAYMENTS))) {
                while (!posted.contains("PAY-3")) {
                    TextMessage message = (TextMessage) consumer.receive(10_000L);
                    assertThat(message).as("delivery after %s", deliveries).isNotNull();
                    Payment payment = mapper.readValue(message.getText(), Payment.class);
                    deliveries.add(new Delivery(payment.id(), message.getJMSRedelivered(),
                            message.getIntProperty("JMSXDeliveryCount")));
                    if (payment.amountCents() > 0) {
                        posted.add(payment.id());
                        ledger.commit();
                    } else {
                        ledger.rollback();
                    }
                }
            }
            assertThat(posted).containsExactly("PAY-1", "PAY-3");
            assertThat(deliveries).containsExactly(
                    new Delivery("PAY-1", false, 1),
                    new Delivery("PAY-2", false, 1),
                    new Delivery("PAY-2", true, 2),
                    new Delivery("PAY-2", true, 3),
                    new Delivery("PAY-3", false, 1));

            Session operator = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            try (MessageConsumer dlq = operator.createConsumer(operator.createQueue(DEAD_LETTERS))) {
                TextMessage dead = (TextMessage) dlq.receive(10_000L);
                assertThat(dead).isNotNull();
                Payment rejected = mapper.readValue(dead.getText(), Payment.class);
                assertThat(rejected).isEqualTo(new Payment("PAY-2", "acct-2", -75));
                assertThat(dead.getJMSCorrelationID()).isEqualTo("PAY-2");
                assertThat(dead.getStringProperty("dlqDeliveryFailureCause"))
                        .contains("exceeds redelivery policy limit");
            }
            assertThat(browse(operator, operator.createQueue(PAYMENTS))).isEmpty();
            assertThat(browse(operator, operator.createQueue(DEAD_LETTERS))).isEmpty();
        } finally {
            connection.close();
        }
    }

    /// Retries ledger payments twice without delay; every other destination keeps its default.
    private static RedeliveryPolicyMap redeliveryPolicies() {
        RedeliveryPolicy ledgerPolicy = new RedeliveryPolicy();
        ledgerPolicy.setQueue("payments.>");
        ledgerPolicy.setMaximumRedeliveries(MAXIMUM_REDELIVERIES);
        ledgerPolicy.setInitialRedeliveryDelay(0L);
        ledgerPolicy.setRedeliveryDelay(0L);
        ledgerPolicy.setUseExponentialBackOff(false);

        RedeliveryPolicyMap policies = new RedeliveryPolicyMap();
        policies.setDefaultEntry(new RedeliveryPolicy());
        policies.setRedeliveryPolicyEntries(List.of(ledgerPolicy));
        return policies;
    }

    private void send(Session session, MessageProducer producer, Payment payment)
            throws Exception {
        TextMessage message = session.createTextMessage(mapper.writeValueAsString(payment));
        message.setJMSCorrelationID(payment.id());
        producer.send(message);
    }

    /// Lists the correlation IDs of the messages still queued, in delivery order.
    private static List<String> browse(Session session, Queue queue) throws Exception {
        List<String> ids = new ArrayList<>();
        try (QueueBrowser browser = session.createBrowser(queue)) {
            var queued = browser.getEnumeration();
            while (queued.hasMoreElements()) {
                ids.add(((Message) queued.nextElement()).getJMSCorrelationID());
            }
        }
        return ids;
    }

    /// One payment instruction, carried as the JSON body of a text message.
    record Payment(String id, String account, long amountCents) {
    }

    /// What the ledger observed about one delivery attempt.
    record Delivery(String paymentId, boolean redelivered, int deliveryCount) {
    }
}
