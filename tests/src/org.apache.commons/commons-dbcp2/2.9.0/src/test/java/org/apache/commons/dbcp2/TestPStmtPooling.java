/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2;

import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.management.ObjectName;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
public class TestPStmtPooling {
    private DataSource createPoolingDataSource() throws Exception {
        DriverManager.registerDriver(new TesterDriver());
        final ConnectionFactory connFactory = new DriverManagerConnectionFactory("jdbc:apache:commons:testdriver", "u1", "p1");
        final PoolableConnectionFactory pcf =
                new PoolableConnectionFactory(connFactory, null);
        pcf.setPoolStatements(true);
        pcf.setDefaultReadOnly(Boolean.FALSE);
        pcf.setDefaultAutoCommit(Boolean.TRUE);
        final ObjectPool<PoolableConnection> connPool = new GenericObjectPool<>(pcf);
        pcf.setPool(connPool);
        return new PoolingDataSource<>(connPool);

    }

    private PoolablePreparedStatement<?> getPoolablePreparedStatement(Statement s) {
        while (s != null) {
            if (s instanceof PoolablePreparedStatement) {
                return (PoolablePreparedStatement<?>) s;
            }
            if (!(s instanceof DelegatingPreparedStatement)) {
                return null;
            }
            s = ((DelegatingPreparedStatement) s).getDelegate();
        }
        return null;
    }


    @Test
    public void testBatchUpdate() throws Exception {
        DriverManager.registerDriver(new TesterDriver());
        final ConnectionFactory connFactory = new DriverManagerConnectionFactory("jdbc:apache:commons:testdriver", "u1", "p1");
        final PoolableConnectionFactory pcf = new PoolableConnectionFactory(connFactory, null);
        pcf.setPoolStatements(true);
        pcf.setDefaultReadOnly(Boolean.FALSE);
        pcf.setDefaultAutoCommit(Boolean.TRUE);
        final ObjectPool<PoolableConnection> connPool = new GenericObjectPool<>(pcf);
        pcf.setPool(connPool);
        final PoolingDataSource<?> ds = new PoolingDataSource<>(connPool);
        final Connection conn = ds.getConnection();
        final PreparedStatement ps = conn.prepareStatement("select 1 from dual");
        final Statement inner = ((DelegatingPreparedStatement) ps).getInnermostDelegate();
        ps.addBatch();
        ps.close();
        conn.close();
        Assertions.assertFalse(inner.isClosed());
        ds.close();
    }


    @Test
    public void testCallableStatementPooling() throws Exception {
        DriverManager.registerDriver(new TesterDriver());
        final ConnectionFactory connFactory = new DriverManagerConnectionFactory(
                "jdbc:apache:commons:testdriver", "u1", "p1");
        final ObjectName oName = new ObjectName("UnitTests:DataSource=test");
        final PoolableConnectionFactory pcf = new PoolableConnectionFactory(connFactory, oName);
        pcf.setPoolStatements(true);
        pcf.setDefaultReadOnly(Boolean.FALSE);
        pcf.setDefaultAutoCommit(Boolean.TRUE);
        final GenericObjectPoolConfig<PoolableConnection> config = new GenericObjectPoolConfig<>();
        config.setJmxNameBase("UnitTests:DataSource=test,connectionpool=connections");
        config.setJmxNamePrefix("");
        final ObjectPool<PoolableConnection> connPool = new GenericObjectPool<>(pcf, config);
        pcf.setPool(connPool);
        final PoolingDataSource<?> ds = new PoolingDataSource<>(connPool);
        try (Connection conn = ds.getConnection()) {
            final Statement stmt1 = conn.prepareStatement("select 1 from dual");
            final Statement ustmt1 = ((DelegatingStatement) stmt1).getInnermostDelegate();
            final Statement cstmt1 = conn.prepareCall("{call home}");
            final Statement ucstmt1 = ((DelegatingStatement) cstmt1).getInnermostDelegate();
            stmt1.close();
            cstmt1.close();
            final Statement stmt2 = conn.prepareStatement("select 1 from dual");
            final Statement ustmt2 = ((DelegatingStatement) stmt2).getInnermostDelegate();
            final Statement cstmt2 = conn.prepareCall("{call home}");
            final Statement ucstmt2 = ((DelegatingStatement) cstmt2).getInnermostDelegate();
            stmt2.close();
            cstmt2.close();
            Assertions.assertSame(ustmt1, ustmt2);
            Assertions.assertSame(ucstmt1, ucstmt2);
            final Statement stmt3 = conn.prepareCall("select 1 from dual");
            final Statement ustmt3 = ((DelegatingStatement) stmt3).getInnermostDelegate();
            stmt3.close();
            Assertions.assertNotSame(ustmt1, ustmt3);
            Assertions.assertNotSame(ustmt3, ucstmt1);
        }
        ds.close();
    }

    @Test
    public void testClosePool() throws Exception {
        DriverManager.registerDriver(new TesterDriver());
        final ConnectionFactory connFactory = new DriverManagerConnectionFactory("jdbc:apache:commons:testdriver", "u1", "p1");
        final PoolableConnectionFactory pcf = new PoolableConnectionFactory(connFactory, null);
        pcf.setPoolStatements(true);
        pcf.setDefaultReadOnly(Boolean.FALSE);
        pcf.setDefaultAutoCommit(Boolean.TRUE);
        final ObjectPool<PoolableConnection> connPool = new GenericObjectPool<>(pcf);
        pcf.setPool(connPool);
        final PoolingDataSource<?> ds = new PoolingDataSource<>(connPool);
        ds.setAccessToUnderlyingConnectionAllowed(true);
        final Connection conn = ds.getConnection();
        try (Statement ignored = conn.prepareStatement("select 1 from dual")) {
            assertNotNull(ignored);
        }
        final Connection poolableConnection = ((DelegatingConnection<?>) conn).getDelegate();
        final Connection poolingConnection = ((DelegatingConnection<?>) poolableConnection).getDelegate();
        poolingConnection.close();
        try (PreparedStatement ignored = conn.prepareStatement("select 1 from dual")) {
            Assertions.fail("Expecting SQLException");
        } catch (final SQLException ex) {
            Assertions.assertTrue(ex.getMessage().endsWith("invalid PoolingConnection."));
        }
        ds.close();
    }

    @Test
    public void testMultipleClose() throws Exception {
        final DataSource ds = createPoolingDataSource();
        final Connection conn = ds.getConnection();
        final PreparedStatement stmt1 = conn.prepareStatement("select 1 from dual");
        final PoolablePreparedStatement<?> pps1 = getPoolablePreparedStatement(stmt1);
        conn.close();
        Assertions.assertTrue(stmt1.isClosed());
        stmt1.close();
        Assertions.assertTrue(stmt1.isClosed());
        final Connection conn2 = ds.getConnection();
        final PreparedStatement stmt2 = conn2.prepareStatement("select 1 from dual");
        Assertions.assertSame(pps1, getPoolablePreparedStatement(stmt2));
        stmt1.close();
        Assertions.assertFalse(stmt2.isClosed());
        stmt2.executeQuery();
        conn2.close();
        Assertions.assertTrue(stmt1.isClosed());
        Assertions.assertTrue(stmt2.isClosed());
    }

    @Test
    public void testStmtPool() throws Exception {
        final DataSource ds = createPoolingDataSource();
        try (Connection conn = ds.getConnection()) {
            final Statement stmt1 = conn.prepareStatement("select 1 from dual");
            final Statement ustmt1 = ((DelegatingStatement) stmt1).getInnermostDelegate();
            stmt1.close();
            final Statement stmt2 = conn.prepareStatement("select 1 from dual");
            final Statement ustmt2 = ((DelegatingStatement) stmt2).getInnermostDelegate();
            stmt2.close();
            Assertions.assertSame(ustmt1, ustmt2);
        }
    }
}
