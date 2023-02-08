/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org_apache_commons.commons_dbcp2.datasources;

import org.apache.commons.dbcp2.DelegatingStatement;
import org.apache.commons.dbcp2.TestConnectionPool;
import org.apache.commons.dbcp2.TesterDriver;
import org.apache.commons.dbcp2.cpdsadapter.DriverAdapterCPDS;
import org.apache.commons.dbcp2.datasources.SharedPoolDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

@SuppressWarnings({"UnusedAssignment", "deprecation", "UnnecessaryLocalVariable", "unused"})
public class TestSharedPoolDataSource extends TestConnectionPool {

    @SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection"})
    private static class CscbString extends PrepareCallCallback {
        @Override
        CallableStatement getCallableStatement() throws SQLException {
            return conn.prepareCall("{call home()}");
        }
    }

    @SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection"})
    private static class CscbStringIntInt extends PrepareCallCallback {
        @Override
        CallableStatement getCallableStatement() throws SQLException {
            return conn.prepareCall("{call home()}", 0, 0);
        }
    }

    @SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection"})
    private static class CscbStringIntIntInt extends PrepareCallCallback {
        @Override
        CallableStatement getCallableStatement() throws SQLException {
            return conn.prepareCall("{call home()}", 0, 0, 0);
        }
    }

    private abstract static class PrepareCallCallback {
        protected Connection conn;

        abstract CallableStatement getCallableStatement() throws SQLException;

        void setConnection(final Connection conn) {
            this.conn = conn;
        }
    }

    private abstract static class PrepareStatementCallback {
        protected Connection conn;

        abstract PreparedStatement getPreparedStatement() throws SQLException;

        void setConnection(final Connection conn) {
            this.conn = conn;
        }
    }

    @SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection"})
    private static class PscbString extends PrepareStatementCallback {
        @Override
        PreparedStatement getPreparedStatement() throws SQLException {
            return conn.prepareStatement("select * from dual");
        }
    }

    @SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection", "MagicConstant"})
    private static class PscbStringInt extends PrepareStatementCallback {
        @Override
        PreparedStatement getPreparedStatement() throws SQLException {
            return conn.prepareStatement("select * from dual", 0);
        }
    }

    @SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection"})
    private static class PscbStringIntArray extends PrepareStatementCallback {
        @Override
        PreparedStatement getPreparedStatement() throws SQLException {
            return conn.prepareStatement("select * from dual", new int[0]);
        }
    }

    @SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection", "MagicConstant"})
    private static class PscbStringIntInt extends PrepareStatementCallback {
        @Override
        PreparedStatement getPreparedStatement() throws SQLException {
            return conn.prepareStatement("select * from dual", 0, 0);
        }
    }

    @SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection", "MagicConstant"})
    private static class PscbStringIntIntInt extends PrepareStatementCallback {
        @Override
        PreparedStatement getPreparedStatement() throws SQLException {
            return conn.prepareStatement("select * from dual", 0, 0, 0);
        }
    }

    @SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection"})
    private static class PscbStringStringArray extends PrepareStatementCallback {
        @Override
        PreparedStatement getPreparedStatement() throws SQLException {
            return conn.prepareStatement("select * from dual", new String[0]);
        }
    }

    private DriverAdapterCPDS pcds;

    private DataSource ds;

    private void doTestPoolCallableStatements(final PrepareCallCallback callBack)
            throws Exception {
        final DriverAdapterCPDS myPcds = new DriverAdapterCPDS();
        myPcds.setDriver("org.apache.commons.dbcp2.TesterDriver");
        myPcds.setUrl("jdbc:apache:commons:testdriver");
        myPcds.setUser("foo");
        myPcds.setPassword("bar");
        myPcds.setPoolPreparedStatements(true);
        myPcds.setMaxPreparedStatements(10);

        final SharedPoolDataSource spDs = new SharedPoolDataSource();
        spDs.setConnectionPoolDataSource(myPcds);
        spDs.setMaxTotal(getMaxTotal());
        spDs.setDefaultMaxWaitMillis((int) getMaxWaitMillis());
        spDs.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        final DataSource myDs = spDs;
        Connection conn = ds.getConnection();
        callBack.setConnection(conn);
        Assertions.assertNotNull(conn);
        CallableStatement stmt = callBack.getCallableStatement();
        Assertions.assertNotNull(stmt);
        final long l1HashCode = ((DelegatingStatement) stmt).getDelegate().hashCode();
        ResultSet rset = stmt.executeQuery();
        Assertions.assertNotNull(rset);
        Assertions.assertTrue(rset.next());
        rset.close();
        stmt.close();
        stmt = callBack.getCallableStatement();
        Assertions.assertNotNull(stmt);
        final long l2HashCode = ((DelegatingStatement) stmt).getDelegate().hashCode();
        rset = stmt.executeQuery();
        Assertions.assertNotNull(rset);
        Assertions.assertTrue(rset.next());
        rset.close();
        stmt.close();
        Assertions.assertTrue(l1HashCode != l2HashCode);
        conn.close();
        conn = null;
        conn = myDs.getConnection();
        callBack.setConnection(conn);
        stmt = callBack.getCallableStatement();
        Assertions.assertNotNull(stmt);
        final long l3HashCode = ((DelegatingStatement) stmt).getDelegate().hashCode();
        rset = stmt.executeQuery();
        Assertions.assertNotNull(rset);
        Assertions.assertTrue(rset.next());
        rset.close();
        stmt.close();
        stmt = callBack.getCallableStatement();
        Assertions.assertNotNull(stmt);
        final long l4HashCode = ((DelegatingStatement) stmt).getDelegate().hashCode();
        rset = stmt.executeQuery();
        Assertions.assertNotNull(rset);
        Assertions.assertTrue(rset.next());
        rset.close();
        stmt.close();
        Assertions.assertEquals(l3HashCode, l4HashCode);
        conn.close();
        conn = null;
        spDs.close();
    }

    private void doTestPoolPreparedStatements(final PrepareStatementCallback callBack) throws Exception {
        final DriverAdapterCPDS mypcds = new DriverAdapterCPDS();
        DataSource myds;
        mypcds.setDriver("org.apache.commons.dbcp2.TesterDriver");
        mypcds.setUrl("jdbc:apache:commons:testdriver");
        mypcds.setUser("foo");
        mypcds.setPassword("bar");
        mypcds.setPoolPreparedStatements(true);
        mypcds.setMaxPreparedStatements(10);

        final SharedPoolDataSource tds = new SharedPoolDataSource();
        tds.setConnectionPoolDataSource(mypcds);
        tds.setMaxTotal(getMaxTotal());
        tds.setDefaultMaxWaitMillis((int) getMaxWaitMillis());
        tds.setDefaultTransactionIsolation(
                Connection.TRANSACTION_READ_COMMITTED);

        myds = tds;

        Connection conn = ds.getConnection();
        callBack.setConnection(conn);
        PreparedStatement stmt;
        ResultSet rset;

        Assertions.assertNotNull(conn);

        stmt = callBack.getPreparedStatement();
        Assertions.assertNotNull(stmt);
        final long l1HashCode = ((DelegatingStatement) stmt).getDelegate().hashCode();
        rset = stmt.executeQuery();
        Assertions.assertNotNull(rset);
        Assertions.assertTrue(rset.next());
        rset.close();
        stmt.close();

        stmt = callBack.getPreparedStatement();
        Assertions.assertNotNull(stmt);
        final long l2HashCode = ((DelegatingStatement) stmt).getDelegate().hashCode();
        rset = stmt.executeQuery();
        Assertions.assertNotNull(rset);
        Assertions.assertTrue(rset.next());
        rset.close();
        stmt.close();
        Assertions.assertTrue(l1HashCode != l2HashCode);
        conn.close();
        conn = null;

        conn = myds.getConnection();
        callBack.setConnection(conn);

        stmt = callBack.getPreparedStatement();
        Assertions.assertNotNull(stmt);
        final long l3HashCode = ((DelegatingStatement) stmt).getDelegate().hashCode();
        rset = stmt.executeQuery();
        Assertions.assertNotNull(rset);
        Assertions.assertTrue(rset.next());
        rset.close();
        stmt.close();

        stmt = callBack.getPreparedStatement();
        Assertions.assertNotNull(stmt);
        final long l4HashCode = ((DelegatingStatement) stmt).getDelegate().hashCode();
        rset = stmt.executeQuery();
        Assertions.assertNotNull(rset);
        Assertions.assertTrue(rset.next());
        rset.close();
        stmt.close();
        Assertions.assertEquals(l3HashCode, l4HashCode);
        conn.close();
        conn = null;
        tds.close();
    }

    @Override
    protected Connection getConnection() throws Exception {
        return ds.getConnection("foo", "bar");
    }

    @BeforeEach
    public void setUp() throws Exception {
        pcds = new DriverAdapterCPDS();
        pcds.setDriver("org.apache.commons.dbcp2.TesterDriver");
        pcds.setUrl("jdbc:apache:commons:testdriver");
        pcds.setUser("foo");
        pcds.setPassword("bar");
        pcds.setPoolPreparedStatements(false);
        pcds.setAccessToUnderlyingConnectionAllowed(true);

        final SharedPoolDataSource tds = new SharedPoolDataSource();
        tds.setConnectionPoolDataSource(pcds);
        tds.setMaxTotal(getMaxTotal());
        tds.setDefaultMaxWaitMillis((int) getMaxWaitMillis());
        tds.setDefaultTransactionIsolation(
                Connection.TRANSACTION_READ_COMMITTED);
        tds.setDefaultAutoCommit(Boolean.TRUE);

        ds = tds;
    }

    @Test
    public void testChangePassword() throws Exception {
        try (Connection c = ds.getConnection("foo", "bay")) {
            Assertions.fail("Should have generated SQLException");
        } catch (final SQLException expected) {
        }
        final Connection con1 = ds.getConnection("foo", "bar");
        final Connection con2 = ds.getConnection("foo", "bar");
        final Connection con3 = ds.getConnection("foo", "bar");
        con1.close();
        con2.close();
        TesterDriver.addUser("foo", "bay");
        try (Connection con4 = ds.getConnection("foo", "bay")) {
            Assertions.assertEquals(0, ((SharedPoolDataSource) ds).getNumIdle(), "Should be no idle connections in the pool");
            con4.close();
            Assertions.assertEquals(1, ((SharedPoolDataSource) ds).getNumIdle(), "Should be one idle connection in the pool");
            try (Connection con4b = ds.getConnection("foo", "bar")) {
                Assertions.fail("Should have generated SQLException");
            } catch (final SQLException expected) {
            }
            final Connection con5 = ds.getConnection("foo", "bay");
            con3.close();
            ds.getConnection("foo", "bay").close();
            Assertions.assertEquals(1, ((SharedPoolDataSource) ds).getNumIdle(), "Should be one idle connection in the pool");
            con5.close();
        } finally {
            TesterDriver.addUser("foo", "bar");
        }
    }

    @Test
    public void testClosePool() throws Exception {
        ((SharedPoolDataSource) ds).close();
        final SharedPoolDataSource tds = new SharedPoolDataSource();
        tds.close();
    }

    @Override
    @Test
    public void testClosing()
            throws Exception {
        final Connection[] c = new Connection[getMaxTotal()];
        for (int i = 0; i < c.length; i++) {
            c[i] = ds.getConnection();
        }
        c[0].close();
        Assertions.assertTrue(c[0].isClosed());
        c[0] = ds.getConnection();
        for (final Connection element : c) {
            element.close();
        }
    }

    @Test
    public void testClosingWithUserName()
            throws Exception {
        final Connection[] c = new Connection[getMaxTotal()];
        for (int i = 0; i < c.length; i++) {
            c[i] = ds.getConnection("u1", "p1");
        }
        c[0].close();
        Assertions.assertTrue(c[0].isClosed());
        c[0] = ds.getConnection("u1", "p1");
        for (final Connection element : c) {
            element.close();
        }
        for (int i = 0; i < c.length; i++) {
            c[i] = ds.getConnection("u1", "p1");
        }
        for (final Connection element : c) {
            element.close();
        }
    }

    @Test
    public void testDbcp369() {
        final ArrayList<SharedPoolDataSource> dataSources = new ArrayList<>();
        for (int j = 0; j < 10000; j++) {
            final SharedPoolDataSource dataSource = new SharedPoolDataSource();
            dataSources.add(dataSource);
        }
        final Thread t1 = new Thread(() -> {
            for (final SharedPoolDataSource dataSource : dataSources) {
                dataSource.setDataSourceName("a");
            }
        });
        final Thread t2 = new Thread(() -> {
            for (final SharedPoolDataSource dataSource : dataSources) {
                try {
                    dataSource.close();
                } catch (final Exception ignored) {
                }
            }
        });
        t1.start();
        t2.start();
        try {
            t1.join();
            t2.join();
        } catch (final InterruptedException ignored) {
        }
    }

    @Test
    public void testIncorrectPassword() throws Exception {
        ds.getConnection("u2", "p2").close();
        try (Connection c = ds.getConnection("u1", "zlsafjk")) {
            Assertions.fail("Able to retrieve connection with incorrect password");
        } catch (final SQLException ignored) {
        }
        ds.getConnection("u1", "p1").close();
        try (Connection c = ds.getConnection("u1", "x")) {
            Assertions.fail("Able to retrieve connection with incorrect password");
        } catch (final SQLException e) {
            if (!e.getMessage().startsWith("Given password did not match")) {
                throw e;
            }
        }
        ds.getConnection("u1", "p1").close();
        ds.getConnection("foo", "bar").close();
        try (Connection c = ds.getConnection("u1", "ar")) {
            Assertions.fail("Should have caused an SQLException");
        } catch (final SQLException expected) {
        }
        try (Connection c = ds.getConnection("u1", "baz")) {
            Assertions.fail("Should have generated SQLException");
        } catch (final SQLException expected) {
        }
    }

    @Override
    @Test
    public void testMaxTotal() throws Exception {
        final Connection[] c = new Connection[getMaxTotal()];
        for (int i = 0; i < c.length; i++) {
            c[i] = ds.getConnection();
            Assertions.assertNotNull(c[i]);
        }
        try (Connection conn = ds.getConnection()) {
            Assertions.fail("Allowed to open more than DefaultMaxTotal connections.");
        } catch (final SQLException ignored) {
        }
        for (final Connection element : c) {
            element.close();
        }
    }

    @Test
    public void testMaxWaitMillis() throws Exception {
        final int maxWaitMillis = 1000;
        final int theadCount = 20;
        ((SharedPoolDataSource) ds).setDefaultMaxWaitMillis(maxWaitMillis);
        final Connection[] c = new Connection[getMaxTotal()];
        for (int i = 0; i < c.length; i++) {
            c[i] = ds.getConnection("foo", "bar");
            Assertions.assertNotNull(c[i]);
        }
        final long startMillis = System.currentTimeMillis();
        final PoolTest[] pts = new PoolTest[theadCount];
        final ThreadGroup threadGroup = new ThreadGroup("testMaxWaitMillis");
        for (int i = 0; i < pts.length; i++) {
            (pts[i] = new PoolTest(threadGroup, 1, true)).start();
        }
        for (final PoolTest poolTest : pts) {
            poolTest.getThread().join();
        }
        final long endMillis = System.currentTimeMillis();
        Assertions.assertTrue(endMillis - startMillis < 2 * maxWaitMillis);
        for (final Connection element : c) {
            element.close();
        }
    }

    @Test
    public void testMultipleThreads1() throws Exception {
        final int defaultMaxWaitMillis = 430;
        ((SharedPoolDataSource) ds).setDefaultMaxWaitMillis(defaultMaxWaitMillis);
        multipleThreads(1, false, false, defaultMaxWaitMillis);
    }

    @Test
    public void testMultipleThreads2() throws Exception {
        final int defaultMaxWaitMillis = 500;
        ((SharedPoolDataSource) ds).setDefaultMaxWaitMillis(defaultMaxWaitMillis);
        multipleThreads(2 * defaultMaxWaitMillis, true, true, defaultMaxWaitMillis);
    }

    @Override
    @Test
    public void testOpening()
            throws Exception {
        final Connection[] c = new Connection[getMaxTotal()];
        for (int i = 0; i < c.length; i++) {
            c[i] = ds.getConnection();
            Assertions.assertNotNull(c[i]);
            for (int j = 0; j <= i; j++) {
                Assertions.assertFalse(c[j].isClosed());
            }
        }
        for (final Connection element : c) {
            element.close();
        }
    }

    @SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection"})
    @Test
    public void testPoolPrepareCall() throws Exception {
        pcds.setPoolPreparedStatements(true);
        final Connection conn = ds.getConnection();
        Assertions.assertNotNull(conn);
        final PreparedStatement stmt = conn.prepareCall("{call home()}");
        Assertions.assertNotNull(stmt);
        final ResultSet rset = stmt.executeQuery();
        Assertions.assertNotNull(rset);
        Assertions.assertTrue(rset.next());
        rset.close();
        stmt.close();
        conn.close();
    }

    @Test
    public void testPoolPreparedCalls() throws Exception {
        doTestPoolCallableStatements(new CscbString());
        doTestPoolCallableStatements(new CscbStringIntInt());
        doTestPoolCallableStatements(new CscbStringIntIntInt());
    }

    @Test
    public void testPoolPreparedStatements() throws Exception {
        doTestPoolPreparedStatements(new PscbString());
        doTestPoolPreparedStatements(new PscbStringIntInt());
        doTestPoolPreparedStatements(new PscbStringInt());
        doTestPoolPreparedStatements(new PscbStringIntArray());
        doTestPoolPreparedStatements(new PscbStringStringArray());
        doTestPoolPreparedStatements(new PscbStringIntIntInt());
    }

    @SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection"})
    @Test
    public void testPoolPrepareStatement() throws Exception {
        pcds.setPoolPreparedStatements(true);
        final Connection conn = ds.getConnection();
        Assertions.assertNotNull(conn);
        final PreparedStatement stmt = conn.prepareStatement("select * from dual");
        Assertions.assertNotNull(stmt);
        final ResultSet rset = stmt.executeQuery();
        Assertions.assertNotNull(rset);
        Assertions.assertTrue(rset.next());
        rset.close();
        stmt.close();
        conn.close();
    }

    @Override
    @Test
    @SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection"})
    public void testSimple() throws Exception {
        final Connection conn = ds.getConnection();
        Assertions.assertNotNull(conn);
        final PreparedStatement stmt = conn.prepareStatement("select * from dual");
        Assertions.assertNotNull(stmt);
        final ResultSet rset = stmt.executeQuery();
        Assertions.assertNotNull(rset);
        Assertions.assertTrue(rset.next());
        rset.close();
        stmt.close();
        conn.close();
    }

    @Override
    @Test
    @SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection"})
    public void testSimple2() throws Exception {
        Connection conn = ds.getConnection();
        Assertions.assertNotNull(conn);
        PreparedStatement stmt = conn.prepareStatement("select * from dual");
        Assertions.assertNotNull(stmt);
        ResultSet rset = stmt.executeQuery();
        Assertions.assertNotNull(rset);
        Assertions.assertTrue(rset.next());
        rset.close();
        stmt.close();
        stmt = conn.prepareStatement("select * from dual");
        Assertions.assertNotNull(stmt);
        rset = stmt.executeQuery();
        Assertions.assertNotNull(rset);
        Assertions.assertTrue(rset.next());
        rset.close();
        stmt.close();
        conn.close();
        try (Statement s = conn.createStatement()) {
            Assertions.fail("Can't use closed connections");
        } catch (final SQLException ignored) {
        }
        conn = ds.getConnection();
        Assertions.assertNotNull(conn);
        stmt = conn.prepareStatement("select * from dual");
        Assertions.assertNotNull(stmt);
        rset = stmt.executeQuery();
        Assertions.assertNotNull(rset);
        Assertions.assertTrue(rset.next());
        rset.close();
        stmt.close();
        stmt = conn.prepareStatement("select * from dual");
        Assertions.assertNotNull(stmt);
        rset = stmt.executeQuery();
        Assertions.assertNotNull(rset);
        Assertions.assertTrue(rset.next());
        rset.close();
        stmt.close();
        conn.close();
        conn = null;
    }

    @Test
    @SuppressWarnings({"SqlNoDataSourceInspection", "SqlDialectInspection"})
    public void testSimpleWithUsername() throws Exception {
        final Connection conn = ds.getConnection("u1", "p1");
        Assertions.assertNotNull(conn);
        final PreparedStatement stmt = conn.prepareStatement("select * from dual");
        Assertions.assertNotNull(stmt);
        final ResultSet rset = stmt.executeQuery();
        Assertions.assertNotNull(rset);
        Assertions.assertTrue(rset.next());
        rset.close();
        stmt.close();
        conn.close();
    }

    @Test
    public void testTransactionIsolationBehavior() throws Exception {
        final Connection conn = getConnection();
        Assertions.assertNotNull(conn);
        Assertions.assertEquals(Connection.TRANSACTION_READ_COMMITTED, conn.getTransactionIsolation());
        conn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
        conn.close();
        final Connection conn2 = getConnection();
        Assertions.assertEquals(Connection.TRANSACTION_READ_COMMITTED, conn2.getTransactionIsolation());
        final Connection conn3 = getConnection();
        Assertions.assertEquals(Connection.TRANSACTION_READ_COMMITTED, conn3.getTransactionIsolation());
        conn2.close();
        conn3.close();
    }
}
