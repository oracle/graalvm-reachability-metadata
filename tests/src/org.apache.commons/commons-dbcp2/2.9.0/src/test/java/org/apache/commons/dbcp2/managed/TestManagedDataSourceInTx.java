/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2.managed;

import org_apache_commons.commons_dbcp2.managed.TestManagedDataSource;
import org.apache.commons.dbcp2.DelegatingConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.transaction.Synchronization;
import javax.transaction.Transaction;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection", "ReassignedVariable", "resource", "ThrowableNotThrown"})
public class TestManagedDataSourceInTx extends TestManagedDataSource {
    @Override
    protected void assertBackPointers(final Connection conn, final Statement statement) throws SQLException {
        assertFalse(conn.isClosed());
        assertFalse(isClosed(statement));
        assertSame(conn, statement.getConnection(),
                "statement.getConnection() should return the exact same connection instance that was used to create the statement");
        final ResultSet resultSet = statement.getResultSet();
        assertFalse(isClosed(resultSet));
        assertSame(statement, resultSet.getStatement(),
                "resultSet.getStatement() should return the exact same statement instance that was used to create the result set");
        final ResultSet executeResultSet = statement.executeQuery("select * from dual");
        assertFalse(isClosed(executeResultSet));
        assertSame(statement, executeResultSet.getStatement(),
                "resultSet.getStatement() should return the exact same statement instance that was used to create the result set");
        final ResultSet keysResultSet = statement.getGeneratedKeys();
        assertFalse(isClosed(keysResultSet));
        assertSame(statement, keysResultSet.getStatement(),
                "resultSet.getStatement() should return the exact same statement instance that was used to create the result set");
        ResultSet preparedResultSet = null;
        if (statement instanceof final PreparedStatement preparedStatement) {
            preparedResultSet = preparedStatement.executeQuery();
            assertFalse(isClosed(preparedResultSet));
            assertSame(statement, preparedResultSet.getStatement(),
                    "resultSet.getStatement() should return the exact same statement instance that was used to create the result set");
        }
        resultSet.getStatement().getConnection().close();
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        transactionManager.begin();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        if (transactionManager.getTransaction() != null) {
            transactionManager.commit();
        }
        super.tearDown();
    }

    @Override
    @Test
    public void testAutoCommitBehavior() throws Exception {
        final Connection connection = newConnection();
        assertFalse(connection.getAutoCommit(), "Auto-commit should be disabled");
        try {
            connection.setAutoCommit(true);
            fail("setAutoCommit method should be disabled while enlisted in a transaction");
        } catch (final SQLException e) {
        }
        assertFalse(connection.getAutoCommit(), "Auto-commit should be disabled");
        connection.close();
    }

    @Override
    @Test
    public void testClearWarnings() throws Exception {
        Connection connection = newConnection();
        assertNotNull(connection);
        final CallableStatement statement = connection.prepareCall("warning");
        assertNotNull(connection.getWarnings());
        final Connection sharedConnection = newConnection();
        assertNotNull(sharedConnection.getWarnings());
        connection.close();
        connection = newConnection();
        assertNotNull(connection.getWarnings());
        assertNotNull(sharedConnection.getWarnings());
        statement.close();
        connection.close();
        sharedConnection.close();
    }

    @Test
    public void testCloseInTransaction() throws Exception {
        final DelegatingConnection<?> connectionA = (DelegatingConnection<?>) newConnection();
        final DelegatingConnection<?> connectionB = (DelegatingConnection<?>) newConnection();
        assertNotEquals(connectionA, connectionB);
        assertNotEquals(connectionB, connectionA);
        assertTrue(connectionA.innermostDelegateEquals(connectionB.getInnermostDelegate()));
        assertTrue(connectionB.innermostDelegateEquals(connectionA.getInnermostDelegate()));
        connectionA.close();
        connectionB.close();
        final Connection connection = newConnection();
        assertFalse(connection.isClosed(), "Connection should be open");
        connection.close();
        assertTrue(connection.isClosed(), "Connection should be closed");
    }

    @Test
    public void testCommit() throws Exception {
        final Connection connection = newConnection();
        assertFalse(connection.isClosed(), "Connection should be open");
        try {
            connection.commit();
            fail("commit method should be disabled while enlisted in a transaction");
        } catch (final SQLException e) {
        }
        assertFalse(connection.isClosed(), "Connection should be open");
        connection.close();
    }

    @Override
    @Test
    public void testConnectionReturnOnCommit() {
    }

    @Override
    @Test
    public void testConnectionsAreDistinct() throws Exception {
        final Connection[] conn = new Connection[getMaxTotal()];
        for (int i = 0; i < conn.length; i++) {
            conn[i] = newConnection();
            for (int j = 0; j < i; j++) {
                assertNotSame(conn[j], conn[i]);
                assertNotEquals(conn[j], conn[i]);
                assertEquals(((DelegatingConnection<?>) conn[j]).getInnermostDelegateInternal(),
                        ((DelegatingConnection<?>) conn[i]).getInnermostDelegateInternal());
            }
        }
        for (final Connection element : conn) {
            element.close();
        }
    }

    @Test
    public void testDoubleReturn() throws Exception {
        transactionManager.getTransaction().registerSynchronization(new Synchronization() {
            private ManagedConnection<?> conn;

            @Override
            public void afterCompletion(final int i) {
                final int numActive = pool.getNumActive();
                try {
                    conn.checkOpen();
                } catch (final Exception e) {
                }
                assertEquals(numActive, pool.getNumActive());
                try {
                    conn.close();
                } catch (final Exception e) {
                    fail("Should have been able to close the connection");
                }
            }

            @Override
            public void beforeCompletion() {
                try {
                    conn = (ManagedConnection<?>) ds.getConnection();
                    assertNotNull(conn);
                } catch (final SQLException e) {
                    fail("Could not get connection");
                }
            }
        });
        transactionManager.commit();
    }

    @Test
    public void testGetConnectionInAfterCompletion() throws Exception {
        final DelegatingConnection<?> connection = (DelegatingConnection<?>) newConnection();
        transactionManager.getTransaction().registerSynchronization(new Synchronization() {
            @Override
            public void afterCompletion(final int i) {
                try {
                    final Connection connection1 = ds.getConnection();
                    try {
                        connection1.getWarnings();
                        fail("Could operate on closed connection");
                    } catch (final SQLException e) {
                    }
                } catch (final SQLException e) {
                    fail("Should have been able to get connection");
                }
            }

            @Override
            public void beforeCompletion() {
            }
        });
        connection.close();
        transactionManager.commit();
    }

    @Override
    @Test
    public void testHashCode() throws Exception {
        final Connection conn1 = newConnection();
        assertNotNull(conn1);
        final Connection conn2 = newConnection();
        assertNotNull(conn2);
        assertNotEquals(conn1.hashCode(), conn2.hashCode());
    }

    @Override
    @Test
    public void testManagedConnectionEqualsFail() {
    }

    @Override
    @Test
    public void testMaxTotal() throws Exception {
        final Transaction[] transactions = new Transaction[getMaxTotal()];
        final Connection[] c = new Connection[getMaxTotal()];
        for (int i = 0; i < c.length; i++) {
            c[i] = newConnection();
            assertNotNull(c[i]);
            transactions[i] = transactionManager.suspend();
            assertNotNull(transactions[i]);
            transactionManager.begin();
        }
        try {
            newConnection();
            fail("Allowed to open more than DefaultMaxTotal connections.");
        } catch (final SQLException e) {
        } finally {
            transactionManager.commit();
            for (int i = 0; i < c.length; i++) {
                transactionManager.resume(transactions[i]);
                c[i].close();
                transactionManager.commit();
            }
        }
    }

    @Override
    @Test
    public void testNestedConnections() {
    }

    @Test
    public void testReadOnly() throws Exception {
        final Connection connection = newConnection();
        assertTrue(connection.isReadOnly(), "Connection be read-only");
        try {
            connection.setReadOnly(true);
            fail("setReadOnly method should be disabled while enlisted in a transaction");
        } catch (final SQLException e) {
        }
        assertTrue(connection.isReadOnly(), "Connection be read-only");
        try {
            connection.setReadOnly(false);
            fail("setReadOnly method should be disabled while enlisted in a transaction");
        } catch (final SQLException e) {
        }
        assertTrue(connection.isReadOnly(), "Connection be read-only");
        connection.close();
    }

    @Override
    @Test
    public void testSharedConnection() throws Exception {
        final DelegatingConnection<?> connectionA = (DelegatingConnection<?>) newConnection();
        final DelegatingConnection<?> connectionB = (DelegatingConnection<?>) newConnection();
        assertNotEquals(connectionA, connectionB);
        assertNotEquals(connectionB, connectionA);
        assertTrue(connectionA.innermostDelegateEquals(connectionB.getInnermostDelegate()));
        assertTrue(connectionB.innermostDelegateEquals(connectionA.getInnermostDelegate()));
        connectionA.close();
        connectionB.close();
    }

    @Test
    public void testSharedTransactionConversion() throws Exception {
        final DelegatingConnection<?> connectionA = (DelegatingConnection<?>) newConnection();
        final DelegatingConnection<?> connectionB = (DelegatingConnection<?>) newConnection();
        assertNotEquals(connectionA, connectionB);
        assertNotEquals(connectionB, connectionA);
        assertTrue(connectionA.innermostDelegateEquals(connectionB.getInnermostDelegate()));
        assertTrue(connectionB.innermostDelegateEquals(connectionA.getInnermostDelegate()));
        transactionManager.commit();
        connectionA.getAutoCommit();
        connectionB.getAutoCommit();
        assertNotEquals(connectionA, connectionB);
        assertNotEquals(connectionB, connectionA);
        assertFalse(connectionA.innermostDelegateEquals(connectionB.getInnermostDelegate()));
        assertFalse(connectionB.innermostDelegateEquals(connectionA.getInnermostDelegate()));
        transactionManager.begin();
        connectionA.getAutoCommit();
        connectionB.getAutoCommit();
        assertNotEquals(connectionA, connectionB);
        assertNotEquals(connectionB, connectionA);
        assertTrue(connectionA.innermostDelegateEquals(connectionB.getInnermostDelegate()));
        assertTrue(connectionB.innermostDelegateEquals(connectionA.getInnermostDelegate()));
        connectionA.close();
        connectionB.close();
    }
}
