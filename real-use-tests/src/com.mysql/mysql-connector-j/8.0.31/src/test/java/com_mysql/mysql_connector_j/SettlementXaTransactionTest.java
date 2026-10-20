/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mysql.mysql_connector_j;

import com.mysql.cj.jdbc.MysqlXADataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.XAConnection;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// XA distributed transactions through `MysqlXADataSource`: a settlement service moves money
/// between a bank database and a clearing database with two-phase commit, rolls back both
/// branches when one fails, and recovers branches left prepared by a crashed coordinator.
public class SettlementXaTransactionTest {

    private static final String IMAGE = "container-registry.oracle.com/mysql/community-server:26.7.1";
    private static final String ROOT_PASSWORD = "root-secret";
    private static final String USER = "settler";
    private static final String PASSWORD = "settler-secret";
    private static final String CONTAINER = "real-use-mysql-settlement-" + UUID.randomUUID();
    private static final int FORMAT_ID = 0x5E77;

    private static String serverUrl;

    @BeforeAll
    static void startMysql() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        docker("run", "--rm", "-d", "--name", CONTAINER,
                "-e", "MYSQL_DATABASE=bank",
                "-e", "MYSQL_USER=" + USER,
                "-e", "MYSQL_PASSWORD=" + PASSWORD,
                "-e", "MYSQL_ROOT_PASSWORD=" + ROOT_PASSWORD,
                "-e", "MYSQL_ROOT_HOST=%",
                "-p", "127.0.0.1:" + port + ":3306",
                IMAGE);
        serverUrl = "jdbc:mysql://127.0.0.1:" + port + "/";
        awaitServer(TimeUnit.MINUTES.toMillis(3));

        // A DBA provisions the second resource and lets the service recover in-doubt branches.
        try (Connection admin = DriverManager.getConnection(serverUrl, "root", ROOT_PASSWORD);
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE clearing");
            statement.execute("GRANT ALL ON clearing.* TO '" + USER + "'@'%'");
            statement.execute("GRANT XA_RECOVER_ADMIN ON *.* TO '" + USER + "'@'%'");
        }
        try (Connection bank = DriverManager.getConnection(serverUrl + "bank", USER, PASSWORD);
             Statement statement = bank.createStatement()) {
            statement.execute("""
                    CREATE TABLE account (
                        owner VARCHAR(32) PRIMARY KEY,
                        balance DECIMAL(12, 2) NOT NULL,
                        CONSTRAINT non_negative CHECK (balance >= 0)
                    ) ENGINE = InnoDB
                    """);
            statement.execute("INSERT INTO account VALUES ('alice', 500.00), ('bob', 80.00)");
        }
        try (Connection clearing = DriverManager.getConnection(serverUrl + "clearing", USER, PASSWORD);
             Statement statement = clearing.createStatement()) {
            statement.execute("""
                    CREATE TABLE payout (
                        reference VARCHAR(32) PRIMARY KEY,
                        payer VARCHAR(32) NOT NULL,
                        amount DECIMAL(12, 2) NOT NULL
                    ) ENGINE = InnoDB
                    """);
        }
    }

    @AfterAll
    static void stopMysql() throws Exception {
        docker("rm", "-f", CONTAINER);
    }

    @Test
    void settlesPayoutsAcrossTwoDatabases() throws Exception {
        MysqlXADataSource bankSource = dataSource("bank");
        MysqlXADataSource clearingSource = dataSource("clearing");

        // A payout that both resources vote for commits in both databases.
        XAConnection bank = bankSource.getXAConnection();
        XAConnection clearing = clearingSource.getXAConnection();
        Settlement first = new Settlement(bank, clearing, "PAY-1");
        first.transfer("alice", new BigDecimal("120.00"));
        assertThat(first.prepare()).containsExactly(XAResource.XA_OK, XAResource.XA_OK);
        first.commit();

        // A payout that overdraws the payer fails in the bank branch, so both branches roll back.
        Settlement overdraft = new Settlement(bank, clearing, "PAY-2");
        assertThatThrownBy(() -> overdraft.transfer("bob", new BigDecimal("95.00")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("non_negative");
        overdraft.abort();

        // The bank resource commits a branch that never touched the clearing database in one phase.
        Xid fee = new BranchId(FORMAT_ID, "FEE-1", "bank");
        XAResource bankResource = bank.getXAResource();
        bankResource.start(fee, XAResource.TMNOFLAGS);
        debit(bank.getConnection(), "bob", new BigDecimal("5.00"));
        bankResource.end(fee, XAResource.TMSUCCESS);
        bankResource.commit(fee, true);

        // The coordinator prepares two payouts, logs a commit decision for one, and then crashes.
        Settlement decided = new Settlement(bank, clearing, "PAY-3");
        decided.transfer("alice", new BigDecimal("30.00"));
        decided.prepare();
        bank.close();
        clearing.close();
        XAConnection orphanBank = bankSource.getXAConnection();
        XAConnection orphanClearing = clearingSource.getXAConnection();
        Settlement undecided = new Settlement(orphanBank, orphanClearing, "PAY-4");
        undecided.transfer("bob", new BigDecimal("10.00"));
        undecided.prepare();
        orphanBank.close();
        orphanClearing.close();
        Set<String> decisionLog = Set.of("PAY-3");

        // Prepared branches stay invisible to readers until the recovery manager resolves them.
        assertThat(balances()).containsExactly("alice=380.00", "bob=75.00");
        assertThat(payouts()).containsExactly("PAY-1:alice:120.00");

        List<String> recovered = new ArrayList<>();
        for (MysqlXADataSource source : List.of(bankSource, clearingSource)) {
            XAConnection recovery = source.getXAConnection();
            try {
                XAResource resource = recovery.getXAResource();
                Xid[] inDoubt = resource.recover(XAResource.TMSTARTRSCAN | XAResource.TMENDRSCAN);
                for (Xid xid : inDoubt) {
                    if (xid.getFormatId() != FORMAT_ID) {
                        continue;
                    }
                    String reference = new String(xid.getGlobalTransactionId(), StandardCharsets.UTF_8);
                    String branch = new String(xid.getBranchQualifier(), StandardCharsets.UTF_8);
                    recovered.add(reference + "/" + branch);
                    if (decisionLog.contains(reference)) {
                        resource.commit(xid, false);
                    } else {
                        resource.rollback(xid);
                    }
                }
                assertThat(resource.recover(XAResource.TMSTARTRSCAN | XAResource.TMENDRSCAN))
                        .noneMatch(xid -> xid.getFormatId() == FORMAT_ID);
                // A branch the server no longer knows is reported as XAER_NOTA.
                Xid resolved = new BranchId(FORMAT_ID, "PAY-3", "bank");
                assertThatThrownBy(() -> resource.commit(resolved, false))
                        .isInstanceOfSatisfying(XAException.class,
                                failure -> assertThat(failure.errorCode).isEqualTo(XAException.XAER_NOTA));
            } finally {
                recovery.close();
            }
        }

        assertThat(recovered).containsExactlyInAnyOrder(
                "PAY-3/bank", "PAY-4/bank", "PAY-3/clearing", "PAY-4/clearing");
        assertThat(balances()).containsExactly("alice=350.00", "bob=75.00");
        assertThat(payouts()).containsExactly("PAY-1:alice:120.00", "PAY-3:alice:30.00");
    }

    /// The application's own transaction identifier: one global id per payout, one branch per database.
    record BranchId(int formatId, String globalId, String branch) implements Xid {

        @Override
        public int getFormatId() {
            return formatId;
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return globalId.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] getBranchQualifier() {
            return branch.getBytes(StandardCharsets.UTF_8);
        }

    }

    /// One payout coordinated over a bank branch and a clearing branch of the same global transaction.
    static final class Settlement {

        private final XAConnection bank;
        private final XAConnection clearing;
        private final String reference;
        private final Xid bankBranch;
        private final Xid clearingBranch;

        Settlement(XAConnection bank, XAConnection clearing, String reference) {
            this.bank = bank;
            this.clearing = clearing;
            this.reference = reference;
            this.bankBranch = new BranchId(FORMAT_ID, reference, "bank");
            this.clearingBranch = new BranchId(FORMAT_ID, reference, "clearing");
        }

        void transfer(String payer, BigDecimal amount) throws SQLException, XAException {
            bank.getXAResource().start(bankBranch, XAResource.TMNOFLAGS);
            clearing.getXAResource().start(clearingBranch, XAResource.TMNOFLAGS);
            try (PreparedStatement insert = clearing.getConnection().prepareStatement(
                    "INSERT INTO payout (reference, payer, amount) VALUES (?, ?, ?)")) {
                insert.setString(1, reference);
                insert.setString(2, payer);
                insert.setBigDecimal(3, amount);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            debit(bank.getConnection(), payer, amount);
            bank.getXAResource().end(bankBranch, XAResource.TMSUCCESS);
            clearing.getXAResource().end(clearingBranch, XAResource.TMSUCCESS);
        }

        List<Integer> prepare() throws SQLException, XAException {
            return List.of(bank.getXAResource().prepare(bankBranch),
                    clearing.getXAResource().prepare(clearingBranch));
        }

        void commit() throws SQLException, XAException {
            bank.getXAResource().commit(bankBranch, false);
            clearing.getXAResource().commit(clearingBranch, false);
        }

        void abort() throws SQLException, XAException {
            bank.getXAResource().end(bankBranch, XAResource.TMFAIL);
            clearing.getXAResource().end(clearingBranch, XAResource.TMFAIL);
            bank.getXAResource().rollback(bankBranch);
            clearing.getXAResource().rollback(clearingBranch);
        }
    }

    private static MysqlXADataSource dataSource(String database) {
        MysqlXADataSource source = new MysqlXADataSource();
        source.setURL(serverUrl + database);
        source.setUser(USER);
        source.setPassword(PASSWORD);
        return source;
    }

    private static void debit(Connection connection, String payer, BigDecimal amount) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE account SET balance = balance - ? WHERE owner = ?")) {
            update.setBigDecimal(1, amount);
            update.setString(2, payer);
            assertThat(update.executeUpdate()).isEqualTo(1);
        }
    }

    private static List<String> balances() throws SQLException {
        List<String> balances = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(serverUrl + "bank", USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT owner, balance FROM account ORDER BY owner")) {
            while (rows.next()) {
                balances.add(rows.getString("owner") + "=" + rows.getBigDecimal("balance").toPlainString());
            }
        }
        return balances;
    }

    private static List<String> payouts() throws SQLException {
        List<String> payouts = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(serverUrl + "clearing", USER, PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT * FROM payout ORDER BY reference")) {
            while (rows.next()) {
                payouts.add(rows.getString("reference") + ":" + rows.getString("payer") + ":"
                        + rows.getBigDecimal("amount").toPlainString());
            }
        }
        return payouts;
    }

    /// Polls with a bounded deadline until the server accepts the application user.
    private static void awaitServer(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        SQLException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection connection = DriverManager.getConnection(serverUrl + "bank", USER, PASSWORD);
                 Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                lastFailure = e;
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("MySQL did not become reachable", lastFailure);
    }

    private static void docker(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).inheritIO().start();
        assertThat(process.waitFor(5, TimeUnit.MINUTES)).as("docker %s finished", args[0]).isTrue();
        assertThat(process.exitValue()).as("docker %s exit code", args[0]).isZero();
    }
}
