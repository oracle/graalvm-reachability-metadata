/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SuppressWarnings({"FieldCanBeLocal", "deprecation", "MagicConstant", "ResultOfMethodCallIgnored", "ThrowableNotThrown", "SqlDialectInspection", "SqlNoDataSourceInspection"})
public class TestDelegatingStatement {

    private static class TesterStatementNonWrapping extends TesterStatement {

        TesterStatementNonWrapping(final Connection conn) {
            super(conn);
        }

        @Override
        public boolean isWrapperFor(final Class<?> iface) {
            return false;
        }
    }

    private DelegatingConnection<Connection> delegatingConnection;
    private TesterConnection testerConnection;
    private Statement mockedStatement;
    private DelegatingStatement delegatingStatement;
    private DelegatingStatement delegatingTesterStatement;
    private TesterResultSet testerResultSet;
    private TesterStatement testerStatement;

    @BeforeEach
    public void setUp() {
        testerConnection = new TesterConnection("test", "test");
        delegatingConnection = new DelegatingConnection<>(testerConnection);
        mockedStatement = mock(Statement.class);
        testerStatement = new TesterStatement(testerConnection);
        delegatingStatement = new DelegatingStatement(delegatingConnection, mockedStatement);
        delegatingTesterStatement = new DelegatingStatement(delegatingConnection, testerStatement);
        testerResultSet = new TesterResultSet(mockedStatement);
    }

    @Test
    public void testAddBatchString() throws Exception {
        try {
            delegatingStatement.addBatch("foo");
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).addBatch("foo");
    }

    @Test
    public void testCancel() throws Exception {
        try {
            delegatingStatement.cancel();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).cancel();
    }

    @Test
    public void testCheckOpen() throws Exception {
        delegatingStatement.checkOpen();
        delegatingStatement.close();
        try {
            delegatingStatement.checkOpen();
            Assertions.fail("Expecting SQLException");
        } catch (final SQLException ex) {
        }
    }

    @Test
    public void testClearBatch() throws Exception {
        try {
            delegatingStatement.clearBatch();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).clearBatch();
    }

    @Test
    public void testClearWarnings() throws Exception {
        try {
            delegatingStatement.clearWarnings();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).clearWarnings();
    }

    @Test
    public void testClose() throws Exception {
        try {
            delegatingStatement.close();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).close();
    }

    @Test
    public void testCloseOnCompletion() throws Exception {
        try {
            delegatingStatement.closeOnCompletion();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).closeOnCompletion();
    }

    @Test
    public void testCloseWithResultSetCloseException() throws Exception {
        try {
            testerResultSet.setSqlExceptionOnClose(true);
            delegatingStatement.addTrace(testerResultSet);
            delegatingStatement.close();
            Assertions.fail("Excpected a SQLExceptionList");
        } catch (final SQLException e) {
            Assertions.assertTrue(e instanceof SQLExceptionList);
        } finally {
            testerResultSet.setSqlExceptionOnClose(false);
        }
        verify(mockedStatement, times(1)).close();
    }

    @Test
    public void testCloseWithStatementCloseException() {
        try {
            testerStatement.setSqlExceptionOnClose(true);
            delegatingTesterStatement.close();
            Assertions.fail("Excpected a SQLExceptionList");
        } catch (final SQLException e) {
            Assertions.assertTrue(e instanceof SQLExceptionList);
        } finally {
            testerStatement.setSqlExceptionOnClose(false);
        }
    }

    @Test
    public void testExecuteBatch() throws Exception {
        try {
            delegatingStatement.executeBatch();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).executeBatch();
    }

    @Test
    public void testExecuteLargeBatch() throws Exception {
        try {
            delegatingStatement.executeLargeBatch();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).executeLargeBatch();
    }

    @Test
    public void testExecuteLargeUpdateString() throws Exception {
        try {
            delegatingStatement.executeLargeUpdate("foo");
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).executeLargeUpdate("foo");
    }

    @Test
    public void testExecuteLargeUpdateStringInteger() throws Exception {
        try {
            delegatingStatement.executeLargeUpdate("foo", 1);
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).executeLargeUpdate("foo", 1);
    }

    @Test
    public void testExecuteLargeUpdateStringIntegerArray() throws Exception {
        try {
            delegatingStatement.executeLargeUpdate("foo", (int[]) null);
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).executeLargeUpdate("foo", (int[]) null);
    }

    @Test
    public void testExecuteLargeUpdateStringStringArray() throws Exception {
        try {
            delegatingStatement.executeLargeUpdate("foo", (String[]) null);
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).executeLargeUpdate("foo", (String[]) null);
    }

    @Test
    public void testExecuteQueryReturnsNull() throws Exception {
        Assertions.assertNull(delegatingStatement.executeQuery("null"));
    }

    @Test
    public void testExecuteQueryString() throws Exception {
        try {
            delegatingStatement.executeQuery("foo");
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).executeQuery("foo");
    }

    @Test
    public void testExecuteString() throws Exception {
        try {
            delegatingStatement.execute("foo");
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).execute("foo");
    }

    @Test
    public void testExecuteStringInteger() throws Exception {
        try {
            delegatingStatement.execute("foo", 1);
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).execute("foo", 1);
    }

    @Test
    public void testExecuteStringIntegerArray() throws Exception {
        try {
            delegatingStatement.execute("foo", (int[]) null);
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).execute("foo", (int[]) null);
    }

    @Test
    public void testExecuteStringStringArray() throws Exception {
        try {
            delegatingStatement.execute("foo", (String[]) null);
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).execute("foo", (String[]) null);
    }

    @Test
    public void testExecuteUpdateString() throws Exception {
        try {
            delegatingStatement.executeUpdate("foo");
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).executeUpdate("foo");
    }

    @Test
    public void testExecuteUpdateStringInteger() throws Exception {
        try {
            delegatingStatement.executeUpdate("foo", 1);
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).executeUpdate("foo", 1);
    }

    @Test
    public void testExecuteUpdateStringIntegerArray() throws Exception {
        try {
            delegatingStatement.executeUpdate("foo", (int[]) null);
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).executeUpdate("foo", (int[]) null);
    }

    @Test
    public void testExecuteUpdateStringStringArray() throws Exception {
        try {
            delegatingStatement.executeUpdate("foo", (String[]) null);
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).executeUpdate("foo", (String[]) null);
    }

    @Test
    public void testGetConnection() throws Exception {
        try {
            delegatingStatement.getConnection();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(0)).getConnection();
    }

    @Test
    public void testGetDelegate() {
        Assertions.assertEquals(mockedStatement, delegatingStatement.getDelegate());
    }

    @Test
    public void testGetFetchDirection() throws Exception {
        try {
            delegatingStatement.getFetchDirection();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).getFetchDirection();
    }

    @Test
    public void testGetFetchSize() throws Exception {
        try {
            delegatingStatement.getFetchSize();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).getFetchSize();
    }

    @Test
    public void testGetGeneratedKeys() throws Exception {
        try {
            delegatingStatement.getGeneratedKeys();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).getGeneratedKeys();
    }

    @Test
    public void testGetLargeMaxRows() throws Exception {
        try {
            delegatingStatement.getLargeMaxRows();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).getLargeMaxRows();
    }

    @Test
    public void testGetLargeUpdateCount() throws Exception {
        try {
            delegatingStatement.getLargeUpdateCount();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).getLargeUpdateCount();
    }

    @Test
    public void testGetMaxFieldSize() throws Exception {
        try {
            delegatingStatement.getMaxFieldSize();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).getMaxFieldSize();
    }

    @Test
    public void testGetMaxRows() throws Exception {
        try {
            delegatingStatement.getMaxRows();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).getMaxRows();
    }

    @Test
    public void testGetMoreResults() throws Exception {
        try {
            delegatingStatement.getMoreResults();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).getMoreResults();
    }

    @Test
    public void testGetMoreResultsInteger() throws Exception {
        try {
            delegatingStatement.getMoreResults(1);
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).getMoreResults(1);
    }

    @Test
    public void testGetQueryTimeout() throws Exception {
        try {
            delegatingStatement.getQueryTimeout();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).getQueryTimeout();
    }

    @Test
    public void testGetResultSet() throws Exception {
        try {
            delegatingStatement.getResultSet();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).getResultSet();
    }

    @Test
    public void testGetResultSetConcurrency() throws Exception {
        try {
            delegatingStatement.getResultSetConcurrency();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).getResultSetConcurrency();
    }

    @Test
    public void testGetResultSetHoldability() throws Exception {
        try {
            delegatingStatement.getResultSetHoldability();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).getResultSetHoldability();
    }

    @Test
    public void testGetResultSetType() throws Exception {
        try {
            delegatingStatement.getResultSetType();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).getResultSetType();
    }

    @Test
    public void testGetUpdateCount() throws Exception {
        try {
            delegatingStatement.getUpdateCount();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).getUpdateCount();
    }

    @Test
    public void testGetWarnings() throws Exception {
        try {
            delegatingStatement.getWarnings();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).getWarnings();
    }

    @Test
    public void testIsClosed() throws Exception {
        try {
            delegatingStatement.isClosed();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(0)).isClosed();
    }

    @Test
    public void testIsCloseOnCompletion() throws Exception {
        try {
            delegatingStatement.isCloseOnCompletion();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).isCloseOnCompletion();
    }

    @Test
    public void testIsPoolable() throws Exception {
        try {
            delegatingStatement.isPoolable();
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).isPoolable();
    }

    @Test
    public void testIsWrapperFor() throws Exception {
        final TesterConnection tstConn = new TesterConnection("test", "test");
        final TesterStatement tstStmt = new TesterStatementNonWrapping(tstConn);
        final DelegatingConnection<TesterConnection> dconn = new DelegatingConnection<>(tstConn);
        final DelegatingStatement stamt = new DelegatingStatement(dconn, tstStmt);
        final Class<?> stmtProxyClass = Proxy.getProxyClass(this.getClass().getClassLoader(), Statement.class);
        Assertions.assertTrue(stamt.isWrapperFor(DelegatingStatement.class));
        Assertions.assertTrue(stamt.isWrapperFor(TesterStatement.class));
        Assertions.assertFalse(stamt.isWrapperFor(stmtProxyClass));

        stamt.close();
    }

    @Test
    public void testSetCursorNameString() throws Exception {
        try {
            delegatingStatement.setCursorName("foo");
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).setCursorName("foo");
    }

    @Test
    public void testSetEscapeProcessingBoolean() throws Exception {
        try {
            delegatingStatement.setEscapeProcessing(Boolean.TRUE);
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).setEscapeProcessing(Boolean.TRUE);
    }

    @Test
    public void testSetFetchDirectionInteger() throws Exception {
        try {
            delegatingStatement.setFetchDirection(1);
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).setFetchDirection(1);
    }

    @Test
    public void testSetFetchSizeInteger() throws Exception {
        try {
            delegatingStatement.setFetchSize(1);
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).setFetchSize(1);
    }

    @Test
    public void testSetLargeMaxRowsLong() throws Exception {
        try {
            delegatingStatement.setLargeMaxRows(1L);
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).setLargeMaxRows(1L);
    }

    @Test
    public void testSetMaxFieldSizeInteger() throws Exception {
        try {
            delegatingStatement.setMaxFieldSize(1);
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).setMaxFieldSize(1);
    }

    @Test
    public void testSetMaxRowsInteger() throws Exception {
        try {
            delegatingStatement.setMaxRows(1);
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).setMaxRows(1);
    }

    @Test
    public void testSetPoolableBoolean() throws Exception {
        try {
            delegatingStatement.setPoolable(Boolean.TRUE);
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).setPoolable(Boolean.TRUE);
    }

    @Test
    public void testSetQueryTimeoutInteger() throws Exception {
        try {
            delegatingStatement.setQueryTimeout(1);
        } catch (final SQLException e) {
        }
        verify(mockedStatement, times(1)).setQueryTimeout(1);
    }

    @Test
    public void testWrap() throws SQLException {
        Assertions.assertEquals(delegatingStatement, delegatingStatement.unwrap(Statement.class));
        Assertions.assertEquals(delegatingStatement, delegatingStatement.unwrap(DelegatingStatement.class));
        Assertions.assertEquals(mockedStatement, delegatingStatement.unwrap(mockedStatement.getClass()));
        Assertions.assertNull(delegatingStatement.unwrap(String.class));
        Assertions.assertTrue(delegatingStatement.isWrapperFor(Statement.class));
        Assertions.assertTrue(delegatingStatement.isWrapperFor(DelegatingStatement.class));
        Assertions.assertTrue(delegatingStatement.isWrapperFor(mockedStatement.getClass()));
        Assertions.assertFalse(delegatingStatement.isWrapperFor(String.class));
    }
}
