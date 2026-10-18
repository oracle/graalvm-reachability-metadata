/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import jakarta.jms.XAConnection;
import jakarta.jms.XAConnectionFactory;
import jakarta.jms.XASession;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQMessageConsumer;
import org.apache.activemq.ActiveMQXAConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.jndi.ActiveMQInitialContextFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.OperationNotSupportedException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Configures a billing service entirely through JNDI with the client's
 * `ActiveMQInitialContextFactory`: named and XA connection factories, per-factory
 * `connection.<name>.*` overrides, broker URL `jms.*` options, configured `queue.` and
 * `topic.` entries, and `dynamicQueues/` lookups.
 *
 * The service reads only the environment a `jndi.properties` file would carry, looks up the
 * factory and destinations by logical name, and sends compressed JSON invoices whose receipt
 * shows that the URL options (compression, queue prefetch) reached the live connection; the
 * XA factory then closes the ledger in a two-phase transaction.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BillingJndiConfigurationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private BrokerService broker;

    private String brokerUrl;

    @BeforeAll
    void startBroker() throws Exception {
        broker = new BrokerService();
        broker.setBrokerName("billing-broker");
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
    void wiresBillingServiceFromJndiEnvironment() throws Exception {
        Context context = new InitialContext(billingEnvironment());
        try {
            assertThat(boundNames(context)).contains(
                    "billingFactory", "XAConnectionFactory", "invoices", "ledgerEvents");

            ConnectionFactory looked = (ConnectionFactory) context.lookup("billingFactory");
            assertThat(looked).isInstanceOf(ActiveMQConnectionFactory.class)
                    .isNotInstanceOf(XAConnectionFactory.class);
            ActiveMQConnectionFactory billingFactory = (ActiveMQConnectionFactory) looked;
            assertThat(billingFactory.getClientIDPrefix()).isEqualTo("billing");
            assertThat(billingFactory.isUseCompression()).isTrue();
            assertThat(billingFactory.getPrefetchPolicy().getQueuePrefetch()).isEqualTo(5);

            Object xaLooked = context.lookup("XAConnectionFactory");
            assertThat(xaLooked).isInstanceOf(ActiveMQXAConnectionFactory.class);
            ActiveMQXAConnectionFactory xaFactory = (ActiveMQXAConnectionFactory) xaLooked;
            assertThat(xaFactory.getClientIDPrefix()).isNotEqualTo("billing");
            assertThat(xaFactory.isUseCompression()).isTrue();

            Queue invoices = (Queue) context.lookup("invoices");
            assertThat(invoices.getQueueName()).isEqualTo("billing.invoices");
            Topic ledgerEvents = (Topic) context.lookup("ledgerEvents");
            assertThat(ledgerEvents.getTopicName()).isEqualTo("billing.ledger.events");
            Queue dynamicInvoices = (Queue) context.lookup("dynamicQueues/billing.invoices");
            assertThat(dynamicInvoices).isEqualTo(invoices);
            Queue refunds = (Queue) context.lookup("dynamicQueues/billing.refunds");
            assertThat(refunds.getQueueName()).isEqualTo("billing.refunds");

            assertThatThrownBy(() -> context.lookup("payroll"))
                    .isInstanceOf(NameNotFoundException.class);
            assertThatThrownBy(() -> context.bind("payroll", invoices))
                    .isInstanceOf(OperationNotSupportedException.class);

            issueInvoices(billingFactory, invoices, dynamicInvoices, refunds);
            closeLedgerInXaTransaction(xaFactory, billingFactory, ledgerEvents);
        } finally {
            context.close();
        }
    }

    /// Sends invoices and a refund through the looked-up destinations and reads them back.
    private void issueInvoices(ActiveMQConnectionFactory factory, Queue invoices,
            Queue dynamicInvoices, Queue refunds) throws Exception {
        List<Invoice> issued = List.of(
                new Invoice("INV-7001", "acme-tools", 1_250_00L,
                        "Quarterly maintenance contract for the north depot ".repeat(20)),
                new Invoice("INV-7002", "globex", 89_90L,
                        "Replacement filters and gaskets for line four ".repeat(20)));
        Invoice refund = new Invoice("RF-7001", "acme-tools", -15_00L, "Duplicate line item");

        Connection connection = factory.createConnection();
        try {
            connection.start();
            ActiveMQConnection openWire = (ActiveMQConnection) connection;
            assertThat(openWire.isUseCompression()).isTrue();
            assertThat(connection.getClientID()).startsWith("billing");

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            try (MessageProducer producer = session.createProducer(null)) {
                for (Invoice invoice : issued) {
                    TextMessage message = session.createTextMessage(mapper.writeValueAsString(invoice));
                    message.setStringProperty("customer", invoice.customer());
                    producer.send(invoices, message);
                }
                producer.send(refunds, session.createTextMessage(mapper.writeValueAsString(refund)));
            }

            try (MessageConsumer consumer = session.createConsumer(dynamicInvoices)) {
                assertThat(((ActiveMQMessageConsumer) consumer).getPrefetchNumber()).isEqualTo(5);
                for (Invoice expected : issued) {
                    TextMessage received = (TextMessage) consumer.receive(10_000L);
                    assertThat(received).as("invoice %s", expected.id()).isNotNull();
                    assertThat(((ActiveMQTextMessage) received).isCompressed()).isTrue();
                    assertThat(received.getStringProperty("customer")).isEqualTo(expected.customer());
                    assertThat(mapper.readValue(received.getText(), Invoice.class)).isEqualTo(expected);
                }
                assertThat(consumer.receiveNoWait()).isNull();
            }

            try (MessageConsumer consumer = session.createConsumer(refunds)) {
                TextMessage received = (TextMessage) consumer.receive(10_000L);
                assertThat(received).isNotNull();
                assertThat(mapper.readValue(received.getText(), Invoice.class)).isEqualTo(refund);
            }
        } finally {
            connection.close();
        }
    }

    /// Closes the quarter's ledger in a two-phase XA transaction and audits the published event.
    private void closeLedgerInXaTransaction(ActiveMQXAConnectionFactory xaFactory,
            ActiveMQConnectionFactory auditFactory, Topic ledgerEvents) throws Exception {
        Connection auditConnection = auditFactory.createConnection();
        XAConnection connection = xaFactory.createXAConnection();
        try {
            auditConnection.start();
            Session auditSession = auditConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer auditor = auditSession.createConsumer(ledgerEvents);

            connection.start();
            XASession xaSession = connection.createXASession();
            assertThat(xaSession.getTransacted()).isTrue();
            XAResource resource = xaSession.getXAResource();
            MessageProducer producer = xaSession.createProducer(ledgerEvents);
            TextMessage event = xaSession.createTextMessage("ledger-closed:2026-Q3");
            assertThatThrownBy(() -> producer.send(event))
                    .isInstanceOf(JMSException.class)
                    .hasMessageContaining("not been enlisted");

            LedgerXid xid = new LedgerXid(0x4c45, "close-2026-Q3".getBytes(UTF_8), new byte[] {1});
            resource.start(xid, XAResource.TMNOFLAGS);
            producer.send(event);
            resource.end(xid, XAResource.TMSUCCESS);
            assertThat(auditor.receiveNoWait()).isNull();
            assertThat(resource.prepare(xid)).isEqualTo(XAResource.XA_OK);
            resource.commit(xid, false);

            TextMessage audited = (TextMessage) auditor.receive(10_000L);
            assertThat(audited).isNotNull();
            assertThat(audited.getText()).isEqualTo("ledger-closed:2026-Q3");
            assertThat(audited.getJMSDestination()).isEqualTo(ledgerEvents);
            assertThat(auditor.receiveNoWait()).isNull();
        } finally {
            connection.close();
            auditConnection.close();
        }
    }

    /// The environment a billing service's `jndi.properties` would carry.
    private Properties billingEnvironment() {
        Properties environment = new Properties();
        environment.setProperty(Context.INITIAL_CONTEXT_FACTORY, ActiveMQInitialContextFactory.class.getName());
        environment.setProperty(Context.PROVIDER_URL,
                brokerUrl + "?jms.useCompression=true&jms.prefetchPolicy.queuePrefetch=5");
        environment.setProperty("connectionFactoryNames", "billingFactory, XAConnectionFactory");
        environment.setProperty("connection.billingFactory.clientIDPrefix", "billing");
        environment.setProperty("queue.invoices", "billing.invoices");
        environment.setProperty("topic.ledgerEvents", "billing.ledger.events");
        return environment;
    }

    /// Lists the names bound at the root of the context.
    private static List<String> boundNames(Context context) throws Exception {
        List<String> names = new ArrayList<>();
        NamingEnumeration<NameClassPair> bindings = context.list("");
        while (bindings.hasMore()) {
            names.add(bindings.next().getName());
        }
        return names;
    }

    /// The global transaction identifier a transaction manager would assign to the ledger close.
    record LedgerXid(int formatId, byte[] globalTransactionId, byte[] branchQualifier)
            implements Xid {

        @Override
        public int getFormatId() {
            return formatId;
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return globalTransactionId;
        }

        @Override
        public byte[] getBranchQualifier() {
            return branchQualifier;
        }
    }

    /// One invoice or refund, carried as the JSON body of a text message.
    record Invoice(String id, String customer, long amountCents, String description) {
    }
}
