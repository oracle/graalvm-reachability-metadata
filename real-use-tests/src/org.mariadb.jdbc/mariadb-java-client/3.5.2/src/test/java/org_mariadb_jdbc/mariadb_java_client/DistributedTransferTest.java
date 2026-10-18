/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mariadb_jdbc.mariadb_java_client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.MariaDbDataSource;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.XAConnection;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Distributed transactions through `MariaDbDataSource` as an `XADataSource`: XA
/// branches on `XAConnection`s, two-phase and one-phase commit, rollback of a failed
/// branch, recovery of prepared branches, and pooled-connection close events.
///
/// The scenario is a transfer coordinator that moves money between accounts kept in
/// two databases of one MariaDB server, acting as the transaction manager itself.
public class DistributedTransferTest {

    private static final String IMAGE = "mariadb:13.0";

    private static final String CONTAINER = "real-use-mariadb-xa-" + UUID.randomUUID();

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

    private static final String CHECKING = "checking_bank";

    private static final String SAVINGS = "savings_bank";

    private static final String USER = "root";

    private static MariaDbDataSource checking;

    private static MariaDbDataSource savings;

    private static final AtomicInteger TRANSACTIONS = new AtomicInteger();

    @BeforeAll
    static void startServer() throws Exception {
        docker("run", "-d", "--name", CONTAINER, "-p", "127.0.0.1::3306",
                "-e", "MARIADB_ALLOW_EMPTY_ROOT_PASSWORD=true",
                IMAGE);
        String mapping = docker("port", CONTAINER, "3306/tcp").lines().findFirst().orElseThrow();
        int port = Integer.parseInt(mapping.substring(mapping.lastIndexOf(':') + 1));

        MariaDbDataSource admin = new MariaDbDataSource("jdbc:mariadb://127.0.0.1:" + port + "/");
        admin.setUser(USER);
        awaitServer(admin);
        try (Connection connection = admin.getConnection(); Statement statement = connection.createStatement()) {
            for (String database : List.of(CHECKING, SAVINGS)) {
                statement.execute("CREATE DATABASE " + database);
                statement.execute("""
                        CREATE TABLE %s.accounts (
                            owner VARCHAR(40) PRIMARY KEY,
                            balance_cents BIGINT NOT NULL CHECK (balance_cents >= 0)
                        ) ENGINE=InnoDB""".formatted(database));
            }
        }

        checking = new MariaDbDataSource("jdbc:mariadb://127.0.0.1:" + port + "/" + CHECKING);
        checking.setUser(USER);
        savings = new MariaDbDataSource("jdbc:mariadb://127.0.0.1:" + port + "/" + SAVINGS);
        savings.setUser(USER);
    }

    @AfterAll
    static void stopServer() throws Exception {
        docker("rm", "-f", "-v", CONTAINER);
    }

    @Test
    void twoPhaseCommitMovesMoneyAcrossBothDatabases() throws Exception {
        TransferCoordinator coordinator = new TransferCoordinator();
        coordinator.openAccount(checking, "ada", 10_000);
        coordinator.openAccount(savings, "ada", 500);

        coordinator.transfer("ada", checking, savings, 2_500);

        assertThat(coordinator.balance(checking, "ada")).isEqualTo(7_500);
        assertThat(coordinator.balance(savings, "ada")).isEqualTo(3_000);
        assertThat(coordinator.inDoubt()).isEmpty();
    }

    @Test
    void failedDebitRollsBackTheCreditBranch() throws Exception {
        TransferCoordinator coordinator = new TransferCoordinator();
        coordinator.openAccount(checking, "grace", 1_000);
        coordinator.openAccount(savings, "grace", 0);

        assertThatThrownBy(() -> coordinator.transfer("grace", checking, savings, 5_000))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("CONSTRAINT");

        assertThat(coordinator.balance(checking, "grace")).isEqualTo(1_000);
        assertThat(coordinator.balance(savings, "grace")).isZero();
        assertThat(coordinator.inDoubt()).isEmpty();
    }

    @Test
    void preparedBranchesSurviveTheCoordinatorAndAreCommittedOnRecovery() throws Exception {
        TransferCoordinator coordinator = new TransferCoordinator();
        coordinator.openAccount(checking, "linus", 4_000);
        coordinator.openAccount(savings, "linus", 1_000);

        TransferXid debitBranch = coordinator.prepareOnly(checking, "linus", -1_500);
        TransferXid creditBranch = coordinator.prepareOnly(savings, "linus", 1_500);

        assertThat(coordinator.balance(checking, "linus")).isEqualTo(4_000);
        List<TransferXid> inDoubt = coordinator.inDoubt();
        assertThat(inDoubt).containsExactlyInAnyOrder(debitBranch, creditBranch);

        assertThat(coordinator.recover()).isEqualTo(2);
        assertThat(coordinator.inDoubt()).isEmpty();
        assertThat(coordinator.balance(checking, "linus")).isEqualTo(2_500);
        assertThat(coordinator.balance(savings, "linus")).isEqualTo(2_500);
    }

    @Test
    void singleBranchUsesOnePhaseCommitAndCannotBeCommittedTwice() throws Exception {
        TransferCoordinator coordinator = new TransferCoordinator();
        coordinator.openAccount(savings, "margaret", 100);

        XAConnection xaConnection = savings.getXAConnection();
        RecordingListener listener = new RecordingListener();
        xaConnection.addConnectionEventListener(listener);
        try {
            XAResource resource = xaConnection.getXAResource();
            TransferXid xid = TransferXid.next(1);
            resource.start(xid, XAResource.TMNOFLAGS);
            try (Connection connection = xaConnection.getConnection()) {
                coordinator.adjust(connection, "margaret", 900);
            }
            resource.end(xid, XAResource.TMSUCCESS);
            resource.commit(xid, true);

            assertThatThrownBy(() -> resource.commit(xid, false))
                    .isInstanceOfSatisfying(XAException.class,
                            e -> assertThat(e.errorCode).isEqualTo(XAException.XAER_NOTA));
            assertThat(resource.isSameRM(resource)).isTrue();
        } finally {
            xaConnection.close();
        }

        assertThat(listener.closed).isEqualTo(1);
        assertThat(listener.errors).isZero();
        assertThat(coordinator.balance(savings, "margaret")).isEqualTo(1_000);
    }

    /// A global transaction identifier minted by the coordinator; branches of one
    /// transfer share the global id and differ in their branch qualifier.
    record TransferXid(int formatId, byte[] globalId, byte[] branchId) implements Xid {

        private static final int FORMAT = 0x52_55_54;

        static TransferXid next(int branch) {
            byte[] global = ("transfer-" + TRANSACTIONS.incrementAndGet()).getBytes(StandardCharsets.US_ASCII);
            return new TransferXid(FORMAT, global, new byte[] {(byte) branch});
        }

        static TransferXid of(Xid xid) {
            return new TransferXid(xid.getFormatId(), xid.getGlobalTransactionId(), xid.getBranchQualifier());
        }

        @Override
        public int getFormatId() {
            return formatId;
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return globalId.clone();
        }

        @Override
        public byte[] getBranchQualifier() {
            return branchId.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TransferXid that && formatId == that.formatId
                    && Arrays.equals(globalId, that.globalId) && Arrays.equals(branchId, that.branchId);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * formatId + Arrays.hashCode(globalId)) + Arrays.hashCode(branchId);
        }
    }

    /// Counts the pooled-connection events a connection pool would react to.
    static final class RecordingListener implements ConnectionEventListener {

        private int closed;

        private int errors;

        @Override
        public void connectionClosed(ConnectionEvent event) {
            closed++;
        }

        @Override
        public void connectionErrorOccurred(ConnectionEvent event) {
            errors++;
        }
    }

    /// The application's transaction manager: it drives one XA branch per database
    /// and decides between commit and rollback for the whole transfer.
    static final class TransferCoordinator {

        void openAccount(MariaDbDataSource bank, String owner, long cents) throws SQLException {
            try (Connection connection = bank.getConnection();
                    PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO accounts (owner, balance_cents) VALUES (?, ?)")) {
                insert.setString(1, owner);
                insert.setLong(2, cents);
                insert.executeUpdate();
            }
        }

        long balance(MariaDbDataSource bank, String owner) throws SQLException {
            try (Connection connection = bank.getConnection();
                    PreparedStatement query = connection.prepareStatement(
                            "SELECT balance_cents FROM accounts WHERE owner = ?")) {
                query.setString(1, owner);
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    return rows.getLong(1);
                }
            }
        }

        void adjust(Connection connection, String owner, long deltaCents) throws SQLException {
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE accounts SET balance_cents = balance_cents + ? WHERE owner = ?")) {
                update.setLong(1, deltaCents);
                update.setString(2, owner);
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
        }

        void transfer(String owner, MariaDbDataSource from, MariaDbDataSource to, long cents) throws Exception {
            XAConnection debitConnection = from.getXAConnection();
            XAConnection creditConnection = to.getXAConnection();
            try {
                XAResource debit = debitConnection.getXAResource();
                XAResource credit = creditConnection.getXAResource();
                TransferXid debitXid = TransferXid.next(1);
                TransferXid creditXid = new TransferXid(debitXid.formatId(), debitXid.globalId(), new byte[] {2});

                credit.start(creditXid, XAResource.TMNOFLAGS);
                debit.start(debitXid, XAResource.TMNOFLAGS);
                try {
                    try (Connection connection = creditConnection.getConnection()) {
                        adjust(connection, owner, cents);
                    }
                    try (Connection connection = debitConnection.getConnection()) {
                        adjust(connection, owner, -cents);
                    }
                } catch (SQLException e) {
                    credit.end(creditXid, XAResource.TMFAIL);
                    debit.end(debitXid, XAResource.TMFAIL);
                    credit.rollback(creditXid);
                    debit.rollback(debitXid);
                    throw e;
                }
                credit.end(creditXid, XAResource.TMSUCCESS);
                debit.end(debitXid, XAResource.TMSUCCESS);

                assertThat(credit.prepare(creditXid)).isEqualTo(XAResource.XA_OK);
                assertThat(debit.prepare(debitXid)).isEqualTo(XAResource.XA_OK);
                credit.commit(creditXid, false);
                debit.commit(debitXid, false);
            } finally {
                debitConnection.close();
                creditConnection.close();
            }
        }

        /// Runs one branch up to the prepare vote and then loses the connection, the
        /// way a coordinator crash between the two phases would.
        TransferXid prepareOnly(MariaDbDataSource bank, String owner, long deltaCents) throws Exception {
            XAConnection xaConnection = bank.getXAConnection();
            try {
                XAResource resource = xaConnection.getXAResource();
                TransferXid xid = TransferXid.next(1);
                resource.start(xid, XAResource.TMNOFLAGS);
                try (Connection connection = xaConnection.getConnection()) {
                    adjust(connection, owner, deltaCents);
                }
                resource.end(xid, XAResource.TMSUCCESS);
                assertThat(resource.prepare(xid)).isEqualTo(XAResource.XA_OK);
                return xid;
            } finally {
                xaConnection.close();
            }
        }

        List<TransferXid> inDoubt() throws Exception {
            XAConnection xaConnection = checking.getXAConnection();
            try {
                List<TransferXid> branches = new ArrayList<>();
                for (Xid xid : xaConnection.getXAResource().recover(XAResource.TMSTARTRSCAN | XAResource.TMENDRSCAN)) {
                    branches.add(TransferXid.of(xid));
                }
                return branches;
            } finally {
                xaConnection.close();
            }
        }

        /// Commits every prepared branch the server still holds, as a restarted
        /// coordinator does once its log says the transfer reached the commit point.
        int recover() throws Exception {
            XAConnection xaConnection = savings.getXAConnection();
            try {
                XAResource resource = xaConnection.getXAResource();
                Xid[] prepared = resource.recover(XAResource.TMSTARTRSCAN | XAResource.TMENDRSCAN);
                for (Xid xid : prepared) {
                    assertThat(xid.getFormatId()).isEqualTo(TransferXid.FORMAT);
                    resource.commit(xid, false);
                }
                return prepared.length;
            } finally {
                xaConnection.close();
            }
        }
    }

    private static void awaitServer(MariaDbDataSource dataSource) throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        SQLException last = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = dataSource.getConnection()) {
                if (connection.isValid(5)) {
                    return;
                }
            } catch (SQLException e) {
                last = e;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("MariaDB did not accept connections in time", last);
    }

    private static String docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        return output;
    }
}
