/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_h2database.h2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Distributed XA transactions: `XAConnection` handles obtained from
 * `org.h2.jdbcx.JdbcDataSource.getXAConnection`, `XAResource` branches driven through start, end,
 * prepare, commit, and rollback, one-phase commit, in-doubt branches listed by `recover` after a
 * database restart, and the pooled-connection events an `XAConnection` raises.
 *
 * A clearing house moves money between two banks that keep their accounts in separate H2
 * databases, acting as the transaction manager that coordinates both branches of each transfer.
 */
public class InterbankTransferXaTransactionsTest {

    private static final int FORMAT_ID = 0x4342;

    @TempDir
    Path directory;

    @Test
    void twoPhaseTransferCommitsOnBothBanks() throws Exception {
        JdbcDataSource northBank = openBank("north", "ACC-1", 500);
        JdbcDataSource southBank = openBank("south", "ACC-9", 100);
        XAConnection north = northBank.getXAConnection();
        XAConnection south = southBank.getXAConnection();
        try {
            TransferXid northBranch = new TransferXid("transfer-1", "north");
            TransferXid southBranch = new TransferXid("transfer-1", "south");
            XAResource northResource = north.getXAResource();
            XAResource southResource = south.getXAResource();

            northResource.start(northBranch, XAResource.TMNOFLAGS);
            southResource.start(southBranch, XAResource.TMNOFLAGS);
            adjustBalance(north.getConnection(), "ACC-1", -150);
            adjustBalance(south.getConnection(), "ACC-9", 150);
            northResource.end(northBranch, XAResource.TMSUCCESS);
            southResource.end(southBranch, XAResource.TMSUCCESS);

            assertEquals(500, balance(northBank, "ACC-1"), "uncommitted debit must stay invisible");
            assertEquals(XAResource.XA_OK, northResource.prepare(northBranch));
            assertEquals(XAResource.XA_OK, southResource.prepare(southBranch));
            northResource.commit(northBranch, false);
            southResource.commit(southBranch, false);
        } finally {
            north.close();
            south.close();
        }

        assertEquals(350, balance(northBank, "ACC-1"));
        assertEquals(250, balance(southBank, "ACC-9"));
    }

    @Test
    void rollbackAfterPrepareLeavesBothBanksUnchanged() throws Exception {
        JdbcDataSource northBank = openBank("north", "ACC-1", 500);
        JdbcDataSource southBank = openBank("south", "ACC-9", 100);
        XAConnection north = northBank.getXAConnection();
        XAConnection south = southBank.getXAConnection();
        try {
            TransferXid northBranch = new TransferXid("transfer-2", "north");
            TransferXid southBranch = new TransferXid("transfer-2", "south");
            XAResource northResource = north.getXAResource();
            XAResource southResource = south.getXAResource();
            assertTrue(northResource.isSameRM(northResource));

            northResource.start(northBranch, XAResource.TMNOFLAGS);
            southResource.start(southBranch, XAResource.TMNOFLAGS);
            // Each handle request rolls back the physical connection, so a branch keeps one handle.
            Connection northHandle = north.getConnection();
            adjustBalance(northHandle, "ACC-1", -700);
            adjustBalance(south.getConnection(), "ACC-9", 700);
            assertEquals(-200, balanceInBranch(northHandle, "ACC-1"));
            northResource.end(northBranch, XAResource.TMSUCCESS);
            southResource.end(southBranch, XAResource.TMSUCCESS);

            northResource.prepare(northBranch);
            southResource.prepare(southBranch);
            // The clearing house rejects the transfer because it would overdraw ACC-1.
            northResource.rollback(northBranch);
            southResource.rollback(southBranch);
        } finally {
            north.close();
            south.close();
        }

        assertEquals(500, balance(northBank, "ACC-1"));
        assertEquals(100, balance(southBank, "ACC-9"));
    }

    @Test
    void singleBankFeeUsesOnePhaseCommit() throws Exception {
        JdbcDataSource northBank = openBank("north", "ACC-1", 500);
        XAConnection north = northBank.getXAConnection();
        try {
            TransferXid fee = new TransferXid("fee-1", "north");
            XAResource resource = north.getXAResource();
            resource.start(fee, XAResource.TMNOFLAGS);
            adjustBalance(north.getConnection(), "ACC-1", -5);
            resource.end(fee, XAResource.TMSUCCESS);
            resource.commit(fee, true);
            assertEquals(0, resource.recover(XAResource.TMSTARTRSCAN | XAResource.TMENDRSCAN).length);
        } finally {
            north.close();
        }

        assertEquals(495, balance(northBank, "ACC-1"));
    }

    @Test
    void preparedBranchSurvivesRestartAndIsRecovered() throws Exception {
        JdbcDataSource northBank = openBank("north", "ACC-1", 500);
        TransferXid branch = new TransferXid("transfer-3", "north");
        XAConnection north = northBank.getXAConnection();
        XAResource resource = north.getXAResource();
        resource.start(branch, XAResource.TMNOFLAGS);
        adjustBalance(north.getConnection(), "ACC-1", -200);
        resource.end(branch, XAResource.TMSUCCESS);
        assertEquals(XAResource.XA_OK, resource.prepare(branch));
        // The bank's database goes down before the clearing house sends its commit decision.
        try (Connection admin = northBank.getConnection(); Statement statement = admin.createStatement()) {
            statement.execute("SHUTDOWN IMMEDIATELY");
        }
        assertThrows(SQLException.class, () -> north.getConnection().createStatement().execute("SELECT 1"));
        north.close();

        XAConnection restarted = northBank.getXAConnection();
        try {
            XAResource recoveredResource = restarted.getXAResource();
            Xid[] inDoubt = recoveredResource.recover(XAResource.TMSTARTRSCAN | XAResource.TMENDRSCAN);
            assertEquals(1, inDoubt.length);
            Xid recovered = inDoubt[0];
            assertEquals(FORMAT_ID, recovered.getFormatId());
            assertArrayEquals(branch.getGlobalTransactionId(), recovered.getGlobalTransactionId());
            assertArrayEquals(branch.getBranchQualifier(), recovered.getBranchQualifier());
            assertEquals(500, balance(northBank, "ACC-1"), "in-doubt debit must stay invisible");

            recoveredResource.commit(recovered, false);
            assertEquals(0, recoveredResource.recover(XAResource.TMSTARTRSCAN).length);
        } finally {
            restarted.close();
        }

        assertEquals(300, balance(northBank, "ACC-1"));
    }

    @Test
    void xaConnectionReportsHandleCloseToItsPool() throws Exception {
        JdbcDataSource northBank = openBank("north", "ACC-1", 500);
        XAConnection north = northBank.getXAConnection();
        PoolEvents events = new PoolEvents();
        north.addConnectionEventListener(events);
        try {
            Connection firstHandle = north.getConnection();
            assertEquals(500, balanceInBranch(firstHandle, "ACC-1"));
            firstHandle.close();
            assertTrue(firstHandle.isClosed());
            assertEquals(List.of("closed"), events.received);

            Connection secondHandle = north.getConnection();
            assertEquals(500, balanceInBranch(secondHandle, "ACC-1"));
            north.removeConnectionEventListener(events);
            secondHandle.close();
            assertEquals(List.of("closed"), events.received);
        } finally {
            north.close();
        }
    }

    private JdbcDataSource openBank(String name, String account, long balance) throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:" + directory.resolve(name + "-" + UUID.randomUUID()).toAbsolutePath());
        dataSource.setUser("sa");
        dataSource.setPassword("");
        dataSource.setDescription(name + " bank ledger");
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE account (
                        id VARCHAR(20) PRIMARY KEY,
                        balance BIGINT NOT NULL
                    )
                    """);
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO account VALUES (?, ?)")) {
                insert.setString(1, account);
                insert.setLong(2, balance);
                insert.executeUpdate();
            }
        }
        return dataSource;
    }

    private static void adjustBalance(Connection connection, String account, long delta) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE account SET balance = balance + ? WHERE id = ?")) {
            update.setLong(1, delta);
            update.setString(2, account);
            assertEquals(1, update.executeUpdate());
        }
    }

    private static long balance(JdbcDataSource dataSource, String account) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return balanceInBranch(connection, account);
        }
    }

    private static long balanceInBranch(Connection connection, String account) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT balance FROM account WHERE id = ?")) {
            query.setString(1, account);
            try (ResultSet rows = query.executeQuery()) {
                assertTrue(rows.next());
                return rows.getLong(1);
            }
        }
    }

    /// The clearing house's transaction identifier: one global id per transfer, one branch per bank.
    private static final class TransferXid implements Xid {

        private final byte[] globalTransactionId;

        private final byte[] branchQualifier;

        TransferXid(String transfer, String bank) {
            this.globalTransactionId = transfer.getBytes(StandardCharsets.UTF_8);
            this.branchQualifier = bank.getBytes(StandardCharsets.UTF_8);
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
    }

    /// Records the events a connection pool would receive from an `XAConnection`.
    private static final class PoolEvents implements ConnectionEventListener {

        private final List<String> received = new ArrayList<>();

        @Override
        public void connectionClosed(ConnectionEvent event) {
            received.add("closed");
        }

        @Override
        public void connectionErrorOccurred(ConnectionEvent event) {
            received.add("error");
        }
    }
}
