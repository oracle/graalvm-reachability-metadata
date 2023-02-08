/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2;

import org_apache_commons.commons_dbcp2.TesterPreparedStatement;
import org.apache.commons.pool2.KeyedObjectPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
public class TestPStmtPoolingBasicDataSource extends TestBasicDataSource {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        ds.setPoolPreparedStatements(true);
        ds.setMaxOpenPreparedStatements(2);
    }

    @Test
    public void testLRUBehavior() throws Exception {
        ds.setMaxOpenPreparedStatements(3);
        final Connection conn = getConnection();
        assertNotNull(conn);
        final PreparedStatement stmt1 = conn.prepareStatement("select 'a' from dual");
        final PreparedStatement inner1 = (PreparedStatement) ((DelegatingPreparedStatement) stmt1).getInnermostDelegate();
        final PreparedStatement stmt2 = conn.prepareStatement("select 'b' from dual");
        final PreparedStatement inner2 = (PreparedStatement) ((DelegatingPreparedStatement) stmt2).getInnermostDelegate();
        final PreparedStatement stmt3 = conn.prepareStatement("select 'c' from dual");
        final PreparedStatement inner3 = (PreparedStatement) ((DelegatingPreparedStatement) stmt3).getInnermostDelegate();
        stmt1.close();
        Thread.sleep(100);
        stmt2.close();
        Thread.sleep(100);
        stmt3.close();
        final PreparedStatement stmt4 = conn.prepareStatement("select 'd' from dual");
        assertNotNull(stmt4);
        try {
            inner1.clearParameters();
            fail("expecting SQLExcption - statement should be closed");
        } catch (final SQLException ex) {
        }
        inner2.clearParameters();
        inner3.clearParameters();
        final PreparedStatement stmt5 = conn.prepareStatement("select 'a' from dual");
        final PreparedStatement inner5 = (PreparedStatement) ((DelegatingPreparedStatement) stmt5).getInnermostDelegate();
        assertNotSame(inner5, inner1);
        try {
            inner2.clearParameters();
            fail("expecting SQLExcption - statement should be closed");
        } catch (final SQLException ex) {
        }
        inner3.clearParameters();
    }

    @Test
    public void testMultipleThreads1() throws Exception {
        ds.setMaxWaitMillis(-1);
        ds.setMaxTotal(5);
        ds.setMaxOpenPreparedStatements(-1);
        multipleThreads(5, false, false, -1, 3, 100, 10000);
    }

    @Test
    public void testPreparedStatementPooling() throws Exception {
        final Connection conn = getConnection();
        assertNotNull(conn);
        final PreparedStatement stmt1 = conn.prepareStatement("select 'a' from dual");
        assertNotNull(stmt1);
        final PreparedStatement stmt2 = conn.prepareStatement("select 'b' from dual");
        assertNotNull(stmt2);
        assertNotSame(stmt1, stmt2);
        try (PreparedStatement ignored = conn.prepareStatement("select 'c' from dual")) {
            fail("expected SQLException");
        } catch (final SQLException e) {
        }
        stmt2.close();
        final PreparedStatement stmt3 = conn.prepareStatement("select 'c' from dual");
        assertNotNull(stmt3);
        assertNotSame(stmt3, stmt1);
        assertNotSame(stmt3, stmt2);
        stmt1.close();
        try (PreparedStatement stmt4 = conn.prepareStatement("select 'a' from dual")) {
            assertNotNull(stmt4);
        }
    }

    @Test
    public void testPStmtCatalog() throws Exception {
        final Connection conn = getConnection();
        conn.setCatalog("catalog1");
        final DelegatingPreparedStatement stmt1 = (DelegatingPreparedStatement) conn.prepareStatement("select 'a' from dual");
        final TesterPreparedStatement inner1 = (TesterPreparedStatement) stmt1.getInnermostDelegate();
        assertEquals("catalog1", inner1.getCatalog());
        stmt1.close();
        conn.setCatalog("catalog2");
        final DelegatingPreparedStatement stmt2 = (DelegatingPreparedStatement) conn.prepareStatement("select 'a' from dual");
        final TesterPreparedStatement inner2 = (TesterPreparedStatement) stmt2.getInnermostDelegate();
        assertEquals("catalog2", inner2.getCatalog());
        stmt2.close();
        conn.setCatalog("catalog1");
        final DelegatingPreparedStatement stmt3 = (DelegatingPreparedStatement) conn.prepareStatement("select 'a' from dual");
        final TesterPreparedStatement inner3 = (TesterPreparedStatement) stmt3.getInnermostDelegate();
        assertEquals("catalog1", inner3.getCatalog());
        stmt3.close();
        assertNotSame(inner1, inner2);
        assertSame(inner1, inner3);
    }

    @Test
    public void testPStmtPoolingAcrossClose() throws Exception {
        ds.setMaxTotal(1);
        ds.setMaxIdle(1);
        ds.setAccessToUnderlyingConnectionAllowed(true);
        final Connection conn1 = getConnection();
        assertNotNull(conn1);
        assertEquals(1, ds.getNumActive());
        assertEquals(0, ds.getNumIdle());
        final PreparedStatement stmt1 = conn1.prepareStatement("select 'a' from dual");
        assertNotNull(stmt1);
        final Statement inner1 = ((DelegatingPreparedStatement) stmt1).getInnermostDelegate();
        assertNotNull(inner1);
        stmt1.close();
        conn1.close();
        assertEquals(0, ds.getNumActive());
        assertEquals(1, ds.getNumIdle());
        final Connection conn2 = getConnection();
        assertNotNull(conn2);
        assertEquals(1, ds.getNumActive());
        assertEquals(0, ds.getNumIdle());
        final PreparedStatement stmt2 = conn2.prepareStatement("select 'a' from dual");
        assertNotNull(stmt2);
        final Statement inner2 = ((DelegatingPreparedStatement) stmt2).getInnermostDelegate();
        assertNotNull(inner2);
        assertSame(inner1, inner2);
    }

    @Test
    public void testPStmtPoolingAcrossCloseWithClearOnReturn() throws Exception {
        ds.setMaxTotal(1);
        ds.setMaxIdle(1);
        ds.setClearStatementPoolOnReturn(true);
        ds.setAccessToUnderlyingConnectionAllowed(true);
        final Connection conn1 = getConnection();
        assertNotNull(conn1);
        assertEquals(1, ds.getNumActive());
        assertEquals(0, ds.getNumIdle());
        @SuppressWarnings("unchecked") final DelegatingConnection<Connection> poolableConn =
                (DelegatingConnection<Connection>) ((DelegatingConnection<Connection>) conn1).getDelegateInternal();
        final KeyedObjectPool<PStmtKey, DelegatingPreparedStatement> stmtPool =
                ((PoolingConnection) poolableConn.getDelegateInternal()).getStatementPool();
        final PreparedStatement stmt1 = conn1.prepareStatement("select 'a' from dual");
        assertNotNull(stmt1);
        final Statement inner1 = ((DelegatingPreparedStatement) stmt1).getInnermostDelegate();
        assertNotNull(inner1);
        stmt1.close();
        final PreparedStatement stmt2 = conn1.prepareStatement("select 'a' from dual");
        assertNotNull(stmt2);
        final Statement inner2 = ((DelegatingPreparedStatement) stmt2).getInnermostDelegate();
        assertNotNull(inner2);
        assertSame(inner1, inner2);
        stmt2.close();
        conn1.close();
        assertTrue(inner1.isClosed());
        assertEquals(0, stmtPool.getNumActive());
        assertEquals(0, stmtPool.getNumIdle());
        assertEquals(0, ds.getNumActive());
        assertEquals(1, ds.getNumIdle());
        final Connection conn2 = getConnection();
        assertNotNull(conn2);
        assertEquals(1, ds.getNumActive());
        assertEquals(0, ds.getNumIdle());
        final PreparedStatement stmt3 = conn2.prepareStatement("select 'a' from dual");
        assertNotNull(stmt3);
        final Statement inner3 = ((DelegatingPreparedStatement) stmt3).getInnermostDelegate();
        assertNotNull(inner3);
        assertNotSame(inner1, inner3);
        conn2.close();
    }

    @Test
    public void testPStmtPoolingWithNoClose() throws Exception {
        ds.setMaxTotal(1);
        ds.setMaxIdle(1);
        ds.setAccessToUnderlyingConnectionAllowed(true);
        final Connection conn1 = getConnection();
        assertNotNull(conn1);
        assertEquals(1, ds.getNumActive());
        assertEquals(0, ds.getNumIdle());
        final PreparedStatement stmt1 = conn1.prepareStatement("select 'a' from dual");
        assertNotNull(stmt1);
        final Statement inner1 = ((DelegatingPreparedStatement) stmt1).getInnermostDelegate();
        assertNotNull(inner1);
        stmt1.close();
        assertNotNull(conn1);
        assertEquals(1, ds.getNumActive());
        assertEquals(0, ds.getNumIdle());
        final PreparedStatement stmt2 = conn1.prepareStatement("select 'a' from dual");
        assertNotNull(stmt2);
        final Statement inner2 = ((DelegatingPreparedStatement) stmt2).getInnermostDelegate();
        assertNotNull(inner2);
        assertSame(inner1, inner2);
    }
}
