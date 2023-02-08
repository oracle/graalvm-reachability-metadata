/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2.managed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLocalXaResource {

    @SuppressWarnings("MagicConstant")
    private static class TestConnection implements Connection {

        public boolean throwWhenGetAutoCommit;
        public boolean throwWhenSetAutoCommit;
        boolean autoCommit;
        boolean readOnly;
        public boolean committed;
        public boolean rolledback;
        public boolean closed;

        @Override
        public void abort(final Executor executor) {
        }

        @Override
        public void clearWarnings() {
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public void commit() {
            committed = true;
        }

        @Override
        public Array createArrayOf(final String typeName, final Object[] elements) {
            return null;
        }

        @Override
        public Blob createBlob() {
            return null;
        }

        @Override
        public Clob createClob() {
            return null;
        }

        @Override
        public NClob createNClob() {
            return null;
        }

        @Override
        public SQLXML createSQLXML() {
            return null;
        }

        @Override
        public Statement createStatement() {
            return null;
        }

        @Override
        public Statement createStatement(final int resultSetType, final int resultSetConcurrency) {
            return null;
        }

        @Override
        public Statement createStatement(final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) {
            return null;
        }

        @Override
        public Struct createStruct(final String typeName, final Object[] attributes) {
            return null;
        }

        @Override
        public boolean getAutoCommit() throws SQLException {
            if (throwWhenGetAutoCommit) {
                throw new SQLException();
            }
            return autoCommit;
        }

        @Override
        public String getCatalog() {
            return null;
        }

        @Override
        public Properties getClientInfo() {
            return null;
        }

        @Override
        public String getClientInfo(final String name) {
            return null;
        }

        @Override
        public int getHoldability() {
            return 0;
        }

        @Override
        public DatabaseMetaData getMetaData() {
            return null;
        }

        @Override
        public int getNetworkTimeout() {
            return 0;
        }

        @Override
        public String getSchema() {
            return null;
        }

        @Override
        public int getTransactionIsolation() {
            return 0;
        }

        @Override
        public Map<String, Class<?>> getTypeMap() {
            return null;
        }

        @Override
        public SQLWarning getWarnings() {
            return null;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public boolean isReadOnly() {
            return readOnly;
        }

        @Override
        public boolean isValid(final int timeout) {
            return false;
        }

        @Override
        public boolean isWrapperFor(final Class<?> iface) {
            return false;
        }

        @Override
        public String nativeSQL(final String sql) {
            return null;
        }

        @Override
        public CallableStatement prepareCall(final String sql) {
            return null;
        }

        @Override
        public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency) {
            return null;
        }

        @Override
        public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency,
                                             final int resultSetHoldability) {
            return null;
        }

        @Override
        public PreparedStatement prepareStatement(final String sql) {
            return null;
        }

        @Override
        public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys) {
            return null;
        }

        @Override
        public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency) {
            return null;
        }

        @Override
        public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency,
                                                  final int resultSetHoldability) {
            return null;
        }

        @Override
        public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes) {
            return null;
        }

        @Override
        public PreparedStatement prepareStatement(final String sql, final String[] columnNames) {
            return null;
        }

        @Override
        public void releaseSavepoint(final Savepoint savepoint) {
        }

        @Override
        public void rollback() {
            rolledback = true;
        }

        @Override
        public void rollback(final Savepoint savepoint) {
        }

        @Override
        public void setAutoCommit(final boolean autoCommit) throws SQLException {
            if (throwWhenSetAutoCommit) {
                throw new SQLException();
            }
            this.autoCommit = autoCommit;
        }

        @Override
        public void setCatalog(final String catalog) {
        }

        @Override
        public void setClientInfo(final Properties properties) {
        }

        @Override
        public void setClientInfo(final String name, final String value) {
        }

        @Override
        public void setHoldability(final int holdability) {
        }

        @Override
        public void setNetworkTimeout(final Executor executor, final int milliseconds) {
        }

        @Override
        public void setReadOnly(final boolean readOnly) {
            this.readOnly = readOnly;
        }

        @Override
        public Savepoint setSavepoint() {
            return null;
        }

        @Override
        public Savepoint setSavepoint(final String name) {
            return null;
        }

        @Override
        public void setSchema(final String schema) {
        }

        @Override
        public void setTransactionIsolation(final int level) {
        }

        @Override
        public void setTypeMap(final Map<String, Class<?>> map) {
        }

        @Override
        public <T> T unwrap(final Class<T> iface) {
            return null;
        }
    }

    private static class TestXid implements Xid {

        @Override
        public byte[] getBranchQualifier() {
            return null;
        }

        @Override
        public int getFormatId() {
            return 0;
        }

        @Override
        public byte[] getGlobalTransactionId() {
            return null;
        }
    }

    private Connection conn;

    private LocalXAConnectionFactory.LocalXAResource resource;

    @BeforeEach
    public void setUp() {
        conn = new TestConnection();
        resource = new LocalXAConnectionFactory.LocalXAResource(conn);
    }

    @Test
    public void testCommit() throws SQLException, XAException {
        final Xid xid = new TestXid();
        ((TestConnection) conn).closed = false;
        conn.setReadOnly(false);
        resource.start(xid, XAResource.TMNOFLAGS);
        resource.commit(xid, false);
        assertTrue(((TestConnection) conn).committed);
    }

    @Test
    public void testCommitConnectionClosed() throws SQLException, XAException {
        final Xid xid = new TestXid();
        ((TestConnection) conn).closed = true;
        conn.setReadOnly(false);
        resource.start(xid, XAResource.TMNOFLAGS);
        assertThrows(XAException.class, () -> resource.commit(xid, false));
    }

    @Test
    public void testCommitConnectionNotReadOnly() throws SQLException, XAException {
        final Xid xid = new TestXid();
        ((TestConnection) conn).closed = false;
        conn.setReadOnly(true);
        resource.start(xid, XAResource.TMNOFLAGS);
        resource.commit(xid, false);
        assertFalse(((TestConnection) conn).committed);
    }

    @Test
    public void testCommitInvalidXid() throws SQLException, XAException {
        final Xid xid = new TestXid();
        ((TestConnection) conn).closed = false;
        conn.setReadOnly(false);
        resource.start(xid, XAResource.TMNOFLAGS);
        assertThrows(XAException.class, () -> resource.commit(new TestXid(), false));
    }

    @Test
    public void testCommitMissingXid() {
        assertThrows(NullPointerException.class, () -> resource.commit(null, false));
    }

    @Test
    public void testCommitNoTransaction() throws SQLException {
        ((TestConnection) conn).closed = false;
        conn.setReadOnly(false);
        assertThrows(XAException.class, () -> resource.commit(new TestXid(), false));
    }

    @Test
    public void testConstructor() {
        assertEquals(0, resource.getTransactionTimeout());
        assertNull(resource.getXid());
        assertFalse(resource.setTransactionTimeout(100));
        assertEquals(0, resource.recover(100).length);
    }

    @Test
    public void testForget() throws XAException {
        final Xid xid = new TestXid();
        resource.start(xid, XAResource.TMNOFLAGS);
        resource.forget(xid);
        assertNull(resource.getXid());
    }

    @Test
    public void testForgetDifferentXid() throws XAException {
        final Xid xid = new TestXid();
        resource.start(xid, XAResource.TMNOFLAGS);
        resource.forget(new TestXid());
        assertEquals(xid, resource.getXid());
    }

    @Test
    public void testForgetMissingXid() throws XAException {
        final Xid xid = new TestXid();
        resource.start(xid, XAResource.TMNOFLAGS);
        resource.forget(null);
        assertEquals(xid, resource.getXid());
    }

    @Test
    public void testIsSame() {
        assertTrue(resource.isSameRM(resource));
        assertFalse(resource.isSameRM(new LocalXAConnectionFactory.LocalXAResource(conn)));
    }

    @Test
    public void testRollback() throws SQLException, XAException {
        final Xid xid = new TestXid();
        ((TestConnection) conn).closed = false;
        conn.setReadOnly(false);
        resource.start(xid, XAResource.TMNOFLAGS);
        resource.rollback(xid);
        assertTrue(((TestConnection) conn).rolledback);
    }

    @Test
    public void testRollbackInvalidXid() throws SQLException, XAException {
        final Xid xid = new TestXid();
        ((TestConnection) conn).closed = false;
        conn.setReadOnly(false);
        resource.start(xid, XAResource.TMNOFLAGS);
        assertThrows(XAException.class, () -> resource.rollback(new TestXid()));
    }

    @Test
    public void testRollbackMissingXid() {
        assertThrows(NullPointerException.class, () -> resource.rollback(null));
    }

    @Test
    public void testStartExceptionOnGetAutoCommit() throws XAException, SQLException {
        final Xid xid = new TestXid();
        ((TestConnection) conn).throwWhenGetAutoCommit = true;
        conn.setAutoCommit(false);
        conn.setReadOnly(true);
        resource.start(xid, XAResource.TMNOFLAGS);
        resource.prepare(xid);
        ((TestConnection) conn).throwWhenGetAutoCommit = false;
        assertTrue(conn.getAutoCommit());
    }

    @Test
    public void testStartFailsWhenCannotSetAutoCommit() {
        final Xid xid = new TestXid();
        ((TestConnection) conn).throwWhenSetAutoCommit = true;
        assertThrows(XAException.class, () -> resource.start(xid, XAResource.TMNOFLAGS));
    }

    @Test
    public void testStartInvalidFlag() {
        assertThrows(XAException.class, () -> resource.start(null, XAResource.TMENDRSCAN));
    }

    @Test
    public void testStartNoFlagButAlreadyEnlisted() throws XAException {
        resource.start(new TestXid(), XAResource.TMNOFLAGS);
        assertThrows(XAException.class, () -> resource.start(new TestXid(), XAResource.TMNOFLAGS));
    }

    @Test
    public void testStartNoFlagResume() throws XAException {
        final Xid xid = new TestXid();
        resource.start(xid, XAResource.TMNOFLAGS);
        resource.start(xid, XAResource.TMRESUME);
        assertEquals(xid, resource.getXid());
    }

    @Test
    public void testStartNoFlagResumeButDifferentXid() throws XAException {
        resource.start(new TestXid(), XAResource.TMNOFLAGS);
        assertThrows(XAException.class, () -> resource.start(new TestXid(), XAResource.TMRESUME));
    }

    @Test
    public void testStartNoFlagResumeEnd() throws XAException {
        final Xid xid = new TestXid();
        resource.start(xid, XAResource.TMNOFLAGS);
        resource.start(xid, XAResource.TMRESUME);
        resource.end(xid, 0);
        assertEquals(xid, resource.getXid());
    }

    @Test
    public void testStartNoFlagResumeEndDifferentXid() throws XAException {
        final Xid xid = new TestXid();
        resource.start(xid, XAResource.TMNOFLAGS);
        resource.start(xid, XAResource.TMRESUME);
        assertThrows(XAException.class, () -> resource.end(new TestXid(), 0));
    }

    @Test
    public void testStartNoFlagResumeEndMissingXid() throws XAException {
        final Xid xid = new TestXid();
        resource.start(xid, XAResource.TMNOFLAGS);
        resource.start(xid, XAResource.TMRESUME);
        assertThrows(NullPointerException.class, () -> resource.end(null, 0));
    }

    @Test
    public void testStartReadOnlyConnectionExceptionOnGetAutoCommit() throws XAException, SQLException {
        final Xid xid = new TestXid();
        ((TestConnection) conn).throwWhenGetAutoCommit = true;
        conn.setAutoCommit(false);
        conn.setReadOnly(false);
        resource.start(xid, XAResource.TMNOFLAGS);
        resource.prepare(xid);
        ((TestConnection) conn).throwWhenGetAutoCommit = false;
        assertFalse(conn.getAutoCommit());
    }

    @Test
    public void testStartReadOnlyConnectionPrepare() throws XAException, SQLException {
        final Xid xid = new TestXid();
        conn.setAutoCommit(false);
        conn.setReadOnly(true);
        resource.start(xid, XAResource.TMNOFLAGS);
        resource.prepare(xid);
        assertFalse(conn.getAutoCommit());
    }
}
