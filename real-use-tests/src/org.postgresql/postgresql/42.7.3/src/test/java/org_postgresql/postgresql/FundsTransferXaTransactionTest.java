/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_postgresql.postgresql;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.ds.common.BaseDataSource;
import org.postgresql.xa.PGXADataSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Coordinates distributed transactions over two PostgreSQL databases through the driver's XA
 * support (`PGXADataSource`, `XAConnection` and its `XAResource`).
 *
 * A transfer service debits an account in the `bank` database and credits it in the `ledger`
 * database as two branches of one global transaction: it prepares both branches and commits them
 * together, rolls both back when one participant votes no, completes a branch with the one-phase
 * optimization, and, acting as a recovering transaction manager after losing its connection,
 * finds an in-doubt prepared branch with `recover` and commits it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FundsTransferXaTransactionTest {

    private static final String POSTGRES_IMAGE = "postgres:18-alpine";

    private static final String BANK = "bank";

    private static final String LEDGER = "ledger";

    private static final String USER = "treasurer";

    private static final String PASSWORD = "s3cret";

    private static final int FORMAT_ID = 0x5452;

    private final String containerName = "real-use-postgresql-" + UUID.randomUUID();

    private int port;

    private PGXADataSource bankSource;

    private PGXADataSource ledgerSource;

    @BeforeAll
    void startDatabase() throws Exception {
        port = freePort();
        Process run = new ProcessBuilder("docker", "run", "-d", "--rm", "--name", containerName,
                "-p", port + ":5432",
                "-e", "POSTGRES_DB=" + BANK,
                "-e", "POSTGRES_USER=" + USER,
                "-e", "POSTGRES_PASSWORD=" + PASSWORD,
                POSTGRES_IMAGE,
                "-c", "max_prepared_transactions=10")
                .inheritIO()
                .start();
        assertThat(run.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(run.exitValue()).isZero();

        awaitDatabase();
        try (Connection connection = plainSource(BANK).getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + LEDGER);
            statement.execute("""
                    CREATE TABLE accounts (
                        id      text          PRIMARY KEY,
                        balance numeric(12,2) NOT NULL CHECK (balance >= 0)
                    )""");
            statement.execute("INSERT INTO accounts VALUES ('alice', 500.00), ('bob', 40.00), ('carol', 100.00)");
        }
        try (Connection connection = plainSource(LEDGER).getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE entries (
                        transfer text          PRIMARY KEY,
                        account  text          NOT NULL,
                        amount   numeric(12,2) NOT NULL
                    )""");
        }

        bankSource = xaSource(BANK);
        ledgerSource = xaSource(LEDGER);
    }

    @AfterAll
    void stopDatabase() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
    }

    @Test
    void transferCommitsBothBranchesAfterTwoPhasePrepare() throws Exception {
        XAConnection bank = bankSource.getXAConnection();
        XAConnection ledger = ledgerSource.getXAConnection();
        try {
            Xid bankBranch = new TransferXid("t-100", 1);
            Xid ledgerBranch = new TransferXid("t-100", 2);
            XAResource bankResource = bank.getXAResource();
            XAResource ledgerResource = ledger.getXAResource();
            assertThat(bankResource.isSameRM(ledgerResource)).isFalse();

            bankResource.start(bankBranch, XAResource.TMNOFLAGS);
            ledgerResource.start(ledgerBranch, XAResource.TMNOFLAGS);
            Connection bankConnection = bank.getConnection();
            assertThat(bankConnection.getAutoCommit()).isFalse();
            assertThat(debit(bankConnection, "alice", "120.00")).isEqualTo(1);
            assertThat(record(ledger.getConnection(), "t-100", "alice", "120.00")).isEqualTo(1);
            assertThatThrownBy(bankConnection::commit).isInstanceOf(SQLException.class);
            bankResource.end(bankBranch, XAResource.TMSUCCESS);
            ledgerResource.end(ledgerBranch, XAResource.TMSUCCESS);

            assertThat(bankResource.prepare(bankBranch)).isEqualTo(XAResource.XA_OK);
            assertThat(ledgerResource.prepare(ledgerBranch)).isEqualTo(XAResource.XA_OK);
            assertThat(preparedTransactions()).isEqualTo(2);
            assertThat(balance("alice")).isEqualByComparingTo("500.00");
            assertThat(ledgerEntry("t-100")).isNull();

            bankResource.commit(bankBranch, false);
            ledgerResource.commit(ledgerBranch, false);
        } finally {
            bank.close();
            ledger.close();
        }

        assertThat(preparedTransactions()).isZero();
        assertThat(balance("alice")).isEqualByComparingTo("380.00");
        assertThat(ledgerEntry("t-100")).isEqualByComparingTo("120.00");
    }

    @Test
    void transferRollsBackBothBranchesWhenOneParticipantVotesNo() throws Exception {
        XAConnection bank = bankSource.getXAConnection();
        XAConnection ledger = ledgerSource.getXAConnection();
        try {
            Xid bankBranch = new TransferXid("t-200", 1);
            Xid ledgerBranch = new TransferXid("t-200", 2);
            XAResource bankResource = bank.getXAResource();
            XAResource ledgerResource = ledger.getXAResource();

            ledgerResource.start(ledgerBranch, XAResource.TMNOFLAGS);
            assertThat(record(ledger.getConnection(), "t-200", "bob", "75.00")).isEqualTo(1);
            ledgerResource.end(ledgerBranch, XAResource.TMSUCCESS);
            assertThat(ledgerResource.prepare(ledgerBranch)).isEqualTo(XAResource.XA_OK);

            bankResource.start(bankBranch, XAResource.TMNOFLAGS);
            assertThatThrownBy(() -> debit(bank.getConnection(), "bob", "75.00"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("accounts_balance_check");
            bankResource.end(bankBranch, XAResource.TMFAIL);

            bankResource.rollback(bankBranch);
            ledgerResource.rollback(ledgerBranch);
        } finally {
            bank.close();
            ledger.close();
        }

        assertThat(preparedTransactions()).isZero();
        assertThat(balance("bob")).isEqualByComparingTo("40.00");
        assertThat(ledgerEntry("t-200")).isNull();
    }

    @Test
    void singleParticipantTransferUsesOnePhaseCommit() throws Exception {
        XAConnection bank = bankSource.getXAConnection();
        try {
            Xid branch = new TransferXid("t-300", 1);
            XAResource resource = bank.getXAResource();
            resource.start(branch, XAResource.TMNOFLAGS);
            assertThat(debit(bank.getConnection(), "carol", "30.00")).isEqualTo(1);
            resource.end(branch, XAResource.TMSUCCESS);
            resource.commit(branch, true);

            Connection local = bank.getConnection();
            local.setAutoCommit(true);
            assertThat(debit(local, "carol", "5.00")).isEqualTo(1);
        } finally {
            bank.close();
        }

        assertThat(preparedTransactions()).isZero();
        assertThat(balance("carol")).isEqualByComparingTo("65.00");
    }

    @Test
    void recoveringManagerCommitsInDoubtBranchFoundAfterConnectionLoss() throws Exception {
        Xid branch = new TransferXid("t-400", 2);
        XAConnection lost = ledgerSource.getXAConnection();
        try {
            XAResource resource = lost.getXAResource();
            resource.start(branch, XAResource.TMNOFLAGS);
            assertThat(record(lost.getConnection(), "t-400", "alice", "10.00")).isEqualTo(1);
            resource.end(branch, XAResource.TMSUCCESS);
            assertThat(resource.prepare(branch)).isEqualTo(XAResource.XA_OK);
        } finally {
            lost.close();
        }
        assertThat(ledgerEntry("t-400")).isNull();

        XAConnection recovery = ledgerSource.getXAConnection();
        try {
            XAResource resource = recovery.getXAResource();
            Xid[] inDoubt = resource.recover(XAResource.TMSTARTRSCAN | XAResource.TMENDRSCAN);
            assertThat(inDoubt).hasSize(1);
            Xid found = inDoubt[0];
            assertThat(found.getFormatId()).isEqualTo(FORMAT_ID);
            assertThat(new String(found.getGlobalTransactionId(), StandardCharsets.US_ASCII)).isEqualTo("t-400");
            assertThat(found.getBranchQualifier()).containsExactly(2);

            resource.commit(found, false);
            assertThat(resource.recover(XAResource.TMSTARTRSCAN | XAResource.TMENDRSCAN)).isEmpty();
        } finally {
            recovery.close();
        }

        assertThat(preparedTransactions()).isZero();
        assertThat(ledgerEntry("t-400")).isEqualByComparingTo("10.00");
    }

    private static int debit(Connection connection, String account, String amount) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE accounts SET balance = balance - ? WHERE id = ?")) {
            update.setBigDecimal(1, new BigDecimal(amount));
            update.setString(2, account);
            return update.executeUpdate();
        }
    }

    private static int record(Connection connection, String transfer, String account, String amount)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO entries (transfer, account, amount) VALUES (?, ?, ?)")) {
            insert.setString(1, transfer);
            insert.setString(2, account);
            insert.setBigDecimal(3, new BigDecimal(amount));
            return insert.executeUpdate();
        }
    }

    private BigDecimal balance(String account) throws SQLException {
        try (Connection connection = plainSource(BANK).getConnection();
                PreparedStatement query = connection.prepareStatement("SELECT balance FROM accounts WHERE id = ?")) {
            query.setString(1, account);
            try (ResultSet row = query.executeQuery()) {
                assertThat(row.next()).isTrue();
                return row.getBigDecimal(1);
            }
        }
    }

    private BigDecimal ledgerEntry(String transfer) throws SQLException {
        try (Connection connection = plainSource(LEDGER).getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT amount FROM entries WHERE transfer = ?")) {
            query.setString(1, transfer);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? row.getBigDecimal(1) : null;
            }
        }
    }

    /// Counts the prepared transactions the server holds across both databases.
    private int preparedTransactions() throws SQLException {
        try (Connection connection = plainSource(BANK).getConnection();
                Statement statement = connection.createStatement();
                ResultSet row = statement.executeQuery("SELECT count(*) FROM pg_prepared_xacts")) {
            assertThat(row.next()).isTrue();
            return row.getInt(1);
        }
    }

    private PGXADataSource xaSource(String database) {
        PGXADataSource source = new PGXADataSource();
        configure(source, database);
        return source;
    }

    private PGSimpleDataSource plainSource(String database) {
        PGSimpleDataSource source = new PGSimpleDataSource();
        configure(source, database);
        return source;
    }

    private void configure(BaseDataSource source, String database) {
        source.setServerNames(new String[] {"127.0.0.1"});
        source.setPortNumbers(new int[] {port});
        source.setDatabaseName(database);
        source.setUser(USER);
        source.setPassword(PASSWORD);
        source.setConnectTimeout(5);
    }

    private void awaitDatabase() throws Exception {
        long deadline = System.currentTimeMillis() + 120_000L;
        SQLException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection probe = plainSource(BANK).getConnection(); Statement statement = probe.createStatement()) {
                statement.execute("SELECT 1");
                return;
            } catch (SQLException e) {
                lastFailure = e;
                Thread.sleep(500L);
            }
        }
        throw new IllegalStateException("PostgreSQL did not accept connections in " + containerName, lastFailure);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /// The transaction manager's identifier of one branch of a global transfer transaction.
    private record TransferXid(byte[] globalTransactionId, byte[] branchQualifier) implements Xid {

        TransferXid(String transfer, int branch) {
            this(transfer.getBytes(StandardCharsets.US_ASCII), new byte[] {(byte) branch});
        }

        @Override
        public int getFormatId() {
            return FORMAT_ID;
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return globalTransactionId.clone();
        }

        @Override
        public byte[] getBranchQualifier() {
            return branchQualifier.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Xid xid
                    && xid.getFormatId() == FORMAT_ID
                    && Arrays.equals(globalTransactionId, xid.getGlobalTransactionId())
                    && Arrays.equals(branchQualifier, xid.getBranchQualifier());
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(globalTransactionId) + Arrays.hashCode(branchQualifier);
        }
    }
}
