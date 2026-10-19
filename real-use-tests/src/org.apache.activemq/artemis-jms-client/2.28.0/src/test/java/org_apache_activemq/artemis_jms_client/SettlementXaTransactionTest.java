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
import org.apache.activemq.artemis.jms.client.ActiveMQXAConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XASession;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Settles payments in XA distributed transactions through `ActiveMQXAConnectionFactory`: the
 * `XASession`'s `XAResource` takes part in two-phase commit, rollback, one-phase commit and
 * crash recovery of prepared branches the way a transaction manager drives it.
 *
 * A settlement service consumes payment instructions and emits ledger entries in one XA branch.
 * A rolled-back branch leaves the instruction queued and the ledger untouched; a prepared branch
 * whose connection is lost is reported by `recover` on a fresh connection and committed there.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SettlementXaTransactionTest {

    static {
        // Like the Artemis distribution, keep the broker audit log off: it is not the subject here.
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.activemq.audit", "warn");
    }

    private static final int FORMAT_ID = 0x5E77;

    private ActiveMQServer broker;

    private ActiveMQXAConnectionFactory connectionFactory;

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
        connectionFactory = new ActiveMQXAConnectionFactory(url + "?callTimeout=10000&consumerWindowSize=0");
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
    void settlesPaymentsAcrossPreparedRolledBackAndRecoveredBranches() throws Exception {
        XAConnectionFactory xaFactory = connectionFactory;
        try (Connection teller = connectionFactory.createConnection()) {
            Session tellerSession = teller.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue instructions = tellerSession.createQueue("settlement.instructions");
            Queue ledger = tellerSession.createQueue("settlement.ledger");
            MessageProducer submitter = tellerSession.createProducer(instructions);
            for (String payment : new String[] {"pay-1:120", "pay-2:75", "pay-3:30", "pay-4:9"}) {
                submitter.send(tellerSession.createTextMessage(payment));
            }
            MessageConsumer ledgerReader = tellerSession.createConsumer(ledger);
            teller.start();

            XAConnection settlement = xaFactory.createXAConnection();
            XASession xaSession = settlement.createXASession();
            assertThat(xaSession.getTransacted()).isTrue();
            XAResource resource = xaSession.getXAResource();
            MessageConsumer incoming = xaSession.createConsumer(instructions);
            MessageProducer bookkeeper = xaSession.createProducer(ledger);
            settlement.start();

            Xid rolledBack = new BranchId("txn-1", "settlement");
            resource.start(rolledBack, XAResource.TMNOFLAGS);
            assertThat(settle(xaSession, incoming, bookkeeper)).isEqualTo("pay-1");
            resource.end(rolledBack, XAResource.TMSUCCESS);
            assertThat(resource.prepare(rolledBack)).isEqualTo(XAResource.XA_OK);
            resource.rollback(rolledBack);
            assertThat(ledgerReader.receiveNoWait()).isNull();

            Xid committed = new BranchId("txn-2", "settlement");
            resource.start(committed, XAResource.TMNOFLAGS);
            TextMessage redelivered = (TextMessage) incoming.receive(10_000);
            assertThat(redelivered.getText()).isEqualTo("pay-1:120");
            assertThat(redelivered.getJMSRedelivered()).isTrue();
            bookkeeper.send(xaSession.createTextMessage("ledger pay-1 120"));
            assertThat(settle(xaSession, incoming, bookkeeper)).isEqualTo("pay-2");
            resource.end(committed, XAResource.TMSUCCESS);
            assertThat(ledgerReader.receiveNoWait()).isNull();
            assertThat(resource.prepare(committed)).isEqualTo(XAResource.XA_OK);
            resource.commit(committed, false);
            assertThat(readLedger(ledgerReader, 2)).containsExactly("ledger pay-1 120", "ledger pay-2 75");

            Xid onePhase = new BranchId("txn-3", "settlement");
            resource.start(onePhase, XAResource.TMNOFLAGS);
            assertThat(settle(xaSession, incoming, bookkeeper)).isEqualTo("pay-3");
            resource.end(onePhase, XAResource.TMSUCCESS);
            resource.commit(onePhase, true);
            assertThat(readLedger(ledgerReader, 1)).containsExactly("ledger pay-3 30");

            Xid inDoubt = new BranchId("txn-4", "settlement");
            resource.start(inDoubt, XAResource.TMNOFLAGS);
            assertThat(settle(xaSession, incoming, bookkeeper)).isEqualTo("pay-4");
            resource.end(inDoubt, XAResource.TMSUCCESS);
            assertThat(resource.prepare(inDoubt)).isEqualTo(XAResource.XA_OK);
            settlement.close();
            assertThat(ledgerReader.receiveNoWait()).isNull();

            XAConnection recovery = xaFactory.createXAConnection();
            try {
                XAResource recoveryResource = recovery.createXASession().getXAResource();
                assertThat(recoveryResource.isSameRM(resource)).isTrue();
                Xid[] prepared = recoveryResource.recover(XAResource.TMSTARTRSCAN | XAResource.TMENDRSCAN);
                assertThat(prepared).hasSize(1);
                assertThat(prepared[0].getFormatId()).isEqualTo(FORMAT_ID);
                assertThat(prepared[0].getGlobalTransactionId()).isEqualTo(inDoubt.getGlobalTransactionId());
                assertThat(prepared[0].getBranchQualifier()).isEqualTo(inDoubt.getBranchQualifier());
                recoveryResource.commit(prepared[0], false);
                assertThat(recoveryResource.recover(XAResource.TMSTARTRSCAN | XAResource.TMENDRSCAN)).isEmpty();
            } finally {
                recovery.close();
            }
            assertThat(readLedger(ledgerReader, 1)).containsExactly("ledger pay-4 9");

            try (MessageConsumer leftover = tellerSession.createConsumer(instructions)) {
                assertThat(leftover.receiveNoWait()).isNull();
            }
        }
    }

    /** Consumes one instruction and books its ledger entry inside the active branch. */
    private static String settle(XASession session, MessageConsumer incoming, MessageProducer bookkeeper)
            throws Exception {
        TextMessage instruction = (TextMessage) incoming.receive(10_000);
        assertThat(instruction).isNotNull();
        String[] parts = instruction.getText().split(":");
        bookkeeper.send(session.createTextMessage("ledger " + parts[0] + " " + parts[1]));
        return parts[0];
    }

    private static String[] readLedger(MessageConsumer ledgerReader, int count) throws Exception {
        String[] entries = new String[count];
        for (int i = 0; i < count; i++) {
            TextMessage entry = (TextMessage) ledgerReader.receive(10_000);
            assertThat(entry).isNotNull();
            entries[i] = entry.getText();
        }
        assertThat(ledgerReader.receiveNoWait()).isNull();
        return entries;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** The branch identifier a transaction manager would hand to the resource. */
    static final class BranchId implements Xid {

        private final byte[] globalTransactionId;

        private final byte[] branchQualifier;

        BranchId(String globalTransactionId, String branchQualifier) {
            this.globalTransactionId = globalTransactionId.getBytes(StandardCharsets.US_ASCII);
            this.branchQualifier = branchQualifier.getBytes(StandardCharsets.US_ASCII);
        }

        @Override
        public int getFormatId() {
            return FORMAT_ID;
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return Arrays.copyOf(globalTransactionId, globalTransactionId.length);
        }

        @Override
        public byte[] getBranchQualifier() {
            return Arrays.copyOf(branchQualifier, branchQualifier.length);
        }
    }
}
