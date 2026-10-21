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
import javax.jms.JMSProducer;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TextMessage;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps per-account ledger postings in order with Artemis message groups: the `JMSXGroupID`
 * property pins a group to one consumer, `JMSXGroupSeq` travels with each message, a closed
 * consumer hands its groups to the next one, and the connection factory's `groupID` URL
 * parameter stamps every message a producer sends.
 *
 * A posting service sends interleaved postings for several accounts to an embedded broker over
 * TCP; competing ledger workers must each see an account's postings in sequence, and only one
 * worker may ever own an account while it is connected.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountLedgerMessageGroupTest {

    static {
        // Like the Artemis distribution, keep the broker audit log off: it is not the subject here.
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.activemq.audit", "warn");
    }

    private ActiveMQServer broker;

    private String url;

    private ActiveMQConnectionFactory connectionFactory;

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
        connectionFactory = new ActiveMQConnectionFactory(url + "?consumerWindowSize=0");
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
    void competingWorkersEachOwnWholeAccountsInSequence() throws Exception {
        List<String> accounts = List.of("ACC-1", "ACC-2", "ACC-3");
        int postingsPerAccount = 4;
        CountDownLatch handled = new CountDownLatch(accounts.size() * postingsPerAccount);
        List<Posting> postings = new CopyOnWriteArrayList<>();

        try (JMSContext alpha = connectionFactory.createContext();
                JMSContext beta = connectionFactory.createContext();
                JMSContext postingService = connectionFactory.createContext()) {
            Queue ledger = postingService.createQueue("ledger.postings");
            listen(alpha, ledger, "alpha", postings, handled);
            listen(beta, ledger, "beta", postings, handled);

            JMSProducer producer = postingService.createProducer();
            for (int sequence = 1; sequence <= postingsPerAccount; sequence++) {
                for (String account : accounts) {
                    producer.setProperty("JMSXGroupID", account)
                            .setProperty("JMSXGroupSeq", sequence)
                            .send(ledger, account + " posting " + sequence);
                }
            }

            assertThat(handled.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(postings).hasSize(accounts.size() * postingsPerAccount);
        for (String account : accounts) {
            List<Posting> forAccount = postings.stream()
                    .filter(posting -> posting.account().equals(account))
                    .toList();
            assertThat(forAccount).extracting(Posting::worker).containsOnly(forAccount.get(0).worker());
            assertThat(forAccount).extracting(Posting::sequence).containsExactly(1, 2, 3, 4);
            assertThat(forAccount).extracting(Posting::body).containsExactly(
                    account + " posting 1", account + " posting 2",
                    account + " posting 3", account + " posting 4");
        }
    }

    @Test
    void closedWorkerHandsItsAccountToTheRemainingWorker() throws Exception {
        try (JMSContext postingService = connectionFactory.createContext();
                JMSContext gamma = connectionFactory.createContext();
                JMSContext delta = connectionFactory.createContext()) {
            Queue ledger = postingService.createQueue("ledger.rebalance");
            JMSProducer producer = postingService.createProducer().setProperty("JMSXGroupID", "ACC-9");
            JMSConsumer gammaConsumer = gamma.createConsumer(ledger);

            producer.send(ledger, "ACC-9 opening balance");
            assertThat(gammaConsumer.receiveBody(String.class, 10_000)).isEqualTo("ACC-9 opening balance");

            JMSConsumer deltaConsumer = delta.createConsumer(ledger);
            for (int i = 1; i <= 3; i++) {
                producer.send(ledger, "ACC-9 posting " + i);
            }
            List<String> gammaBodies = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                gammaBodies.add(gammaConsumer.receiveBody(String.class, 10_000));
            }
            assertThat(gammaBodies).containsExactly("ACC-9 posting 1", "ACC-9 posting 2", "ACC-9 posting 3");
            assertThat(deltaConsumer.receiveNoWait()).isNull();

            gammaConsumer.close();
            producer.send(ledger, "ACC-9 closing balance");
            Message handedOver = deltaConsumer.receive(10_000);
            assertThat(handedOver).isNotNull();
            assertThat(((TextMessage) handedOver).getText()).isEqualTo("ACC-9 closing balance");
            assertThat(handedOver.getStringProperty("JMSXGroupID")).isEqualTo("ACC-9");
        }
    }

    @Test
    void connectionFactoryGroupIdStampsEveryPosting() throws Exception {
        try (ActiveMQConnectionFactory batchFactory = new ActiveMQConnectionFactory(url + "?groupID=nightly-batch");
                JMSContext batchService = batchFactory.createContext();
                JMSContext worker = connectionFactory.createContext()) {
            assertThat(batchFactory.getGroupID()).isEqualTo("nightly-batch");
            Queue ledger = batchService.createQueue("ledger.batch");
            JMSConsumer consumer = worker.createConsumer(ledger);

            JMSProducer producer = batchService.createProducer();
            producer.send(ledger, "interest accrual");
            producer.send(ledger, "fee sweep");

            for (String expected : List.of("interest accrual", "fee sweep")) {
                Message message = consumer.receive(10_000);
                assertThat(message).isNotNull();
                assertThat(((TextMessage) message).getText()).isEqualTo(expected);
                assertThat(message.getStringProperty("JMSXGroupID")).isEqualTo("nightly-batch");
            }
            assertThat(consumer.receiveNoWait()).isNull();
        }
    }

    private static void listen(JMSContext context, Queue queue, String worker, List<Posting> postings,
            CountDownLatch handled) {
        context.createConsumer(queue).setMessageListener(message -> {
            try {
                String account = message.getStringProperty("JMSXGroupID");
                int sequence = message.getIntProperty("JMSXGroupSeq");
                postings.add(new Posting(worker, account, sequence, message.getBody(String.class)));
            } catch (JMSException e) {
                throw new IllegalStateException(e);
            } finally {
                handled.countDown();
            }
        });
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** One ledger posting as the worker that handled it observed it. */
    record Posting(String worker, String account, int sequence, String body) {
    }
}
