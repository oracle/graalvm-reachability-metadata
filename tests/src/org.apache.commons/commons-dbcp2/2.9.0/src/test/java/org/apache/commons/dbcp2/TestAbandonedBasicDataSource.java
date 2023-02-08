/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2;

import org_apache_commons.commons_dbcp2.TesterUtils;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection", "BusyWait", "MagicConstant"})
public class TestAbandonedBasicDataSource extends TestBasicDataSource {
    private StringWriter sw;

    private void assertAndReset(final DelegatingConnection<?> con) {
        assertTrue(con.getLastUsed() > 0);
        con.setLastUsed(0);
    }

    private void checkLastUsedPreparedStatement(final PreparedStatement ps, final DelegatingConnection<?> conn) throws Exception {
        ps.execute();
        assertAndReset(conn);
        assertNotNull(ps.executeQuery());
        assertAndReset(conn);
        ps.executeUpdate();
        assertAndReset(conn);
    }

    private void checkLastUsedStatement(final Statement st, final DelegatingConnection<?> conn) throws Exception {
        st.execute("");
        assertAndReset(conn);
        st.execute("", new int[]{});
        assertAndReset(conn);
        st.execute("", 0);
        assertAndReset(conn);
        st.executeBatch();
        assertAndReset(conn);
        st.executeLargeBatch();
        assertAndReset(conn);
        assertNotNull(st.executeQuery(""));
        assertAndReset(conn);
        st.executeUpdate("");
        assertAndReset(conn);
        st.executeUpdate("", new int[]{});
        assertAndReset(conn);
        st.executeLargeUpdate("", new int[]{});
        assertAndReset(conn);
        st.executeUpdate("", 0);
        assertAndReset(conn);
        st.executeLargeUpdate("", 0);
        assertAndReset(conn);
        st.executeUpdate("", new String[]{});
        assertAndReset(conn);
        st.executeLargeUpdate("", new String[]{});
        assertAndReset(conn);
    }

    private void createStatement(final Connection conn) throws Exception {
        final PreparedStatement ps = conn.prepareStatement("");
        assertNotNull(ps);
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        ds.setLogAbandoned(true);
        ds.setRemoveAbandonedOnBorrow(true);
        ds.setRemoveAbandonedOnMaintenance(true);
        ds.setRemoveAbandonedTimeout(10000);
        sw = new StringWriter();
        ds.setAbandonedLogWriter(new PrintWriter(sw));
    }

    @Test
    public void testAbandoned() throws Exception {
        ds.setRemoveAbandonedTimeout(0);
        ds.setMaxTotal(1);
        for (int i = 0; i < 3; i++) {
            assertNotNull(ds.getConnection());
        }
    }

    @Test
    public void testAbandonedClose() throws Exception {
        ds.setRemoveAbandonedTimeout(0);
        ds.setMaxTotal(1);
        ds.setAccessToUnderlyingConnectionAllowed(true);
        final Connection conn1 = getConnection();
        assertNotNull(conn1);
        assertEquals(1, ds.getNumActive());
        final Connection conn2 = getConnection();
        assertNotNull(conn2);
        assertEquals(1, ds.getNumActive());
        assertTrue(((DelegatingConnection<?>) conn1).getInnermostDelegate().isClosed());
        final TesterConnection tCon = (TesterConnection) ((DelegatingConnection<?>) conn1).getInnermostDelegate();
        assertTrue(tCon.isAborted());
        conn2.close();
        assertEquals(0, ds.getNumActive());
        conn1.close();
        assertEquals(0, ds.getNumActive());
        final String string = sw.toString();
        assertTrue(string.contains("testAbandonedClose"), string);
    }

    @Test
    public void testAbandonedCloseWithExceptions() throws Exception {
        ds.setRemoveAbandonedTimeout(0);
        ds.setMaxTotal(1);
        ds.setAccessToUnderlyingConnectionAllowed(true);
        final Connection conn1 = getConnection();
        assertNotNull(conn1);
        assertEquals(1, ds.getNumActive());
        final Connection conn2 = getConnection();
        assertNotNull(conn2);
        assertEquals(1, ds.getNumActive());
        final TesterConnection tconn1 = (TesterConnection) ((DelegatingConnection<?>) conn1).getInnermostDelegate();
        tconn1.setFailure(new IOException("network error"));
        final TesterConnection tconn2 = (TesterConnection) ((DelegatingConnection<?>) conn2).getInnermostDelegate();
        tconn2.setFailure(new IOException("network error"));
        try {
            conn2.close();
        } catch (final SQLException ignored) {
        }
        assertEquals(0, ds.getNumActive());
        try {
            conn1.close();
        } catch (final SQLException ignored) {
        }
        assertEquals(0, ds.getNumActive());
        final String string = sw.toString();
        assertTrue(string.contains("testAbandonedCloseWithExceptions"), string);
    }

    @Test
    public void testGarbageCollectorCleanUp01() throws Exception {
        final DelegatingConnection<?> conn = (DelegatingConnection<?>) ds.getConnection();
        Assertions.assertEquals(0, conn.getTrace().size());
        createStatement(conn);
        Assertions.assertEquals(1, conn.getTrace().size());
        System.gc();
        Assertions.assertEquals(0, conn.getTrace().size());
    }

    @Test
    @Disabled("https://github.com/oracle/graal/issues/5913")
    public void testGarbageCollectorCleanUp02() throws Exception {
        ds.setPoolPreparedStatements(true);
        ds.setAccessToUnderlyingConnectionAllowed(true);
        final DelegatingConnection<?> conn = (DelegatingConnection<?>) ds.getConnection();
        final PoolableConnection poolableConn = (PoolableConnection) conn.getDelegate();
        final PoolingConnection poolingConn = (PoolingConnection) poolableConn.getDelegate();
        @SuppressWarnings("unchecked") final GenericKeyedObjectPool<PStmtKey, DelegatingPreparedStatement> gkop =
                (GenericKeyedObjectPool<PStmtKey, DelegatingPreparedStatement>) TesterUtils.getField(poolingConn, "pstmtPool");
        Assertions.assertEquals(0, conn.getTrace().size());
        Assertions.assertEquals(0, gkop.getNumActive());
        createStatement(conn);
        Assertions.assertEquals(1, conn.getTrace().size());
        Assertions.assertEquals(1, gkop.getNumActive());
        System.gc();
        int count = 0;
        while (count < 50 && gkop.getNumActive() > 0) {
            Thread.sleep(100);
            count++;
        }
        Assertions.assertEquals(0, gkop.getNumActive());
        Assertions.assertEquals(0, conn.getTrace().size());
    }

    @Test
    public void testLastUsed() throws Exception {
        ds.setRemoveAbandonedTimeout(1);
        ds.setMaxTotal(2);
        try (Connection conn1 = ds.getConnection()) {
            Thread.sleep(500);
            try (Statement ignored1 = conn1.createStatement()) {
                assertNotNull(ignored1);
            }
            Thread.sleep(800);
            final Connection conn2 = ds.getConnection();
            try (Statement ignored = conn1.createStatement()) {
                assertNotNull(ignored);
            }
            conn2.close();
            Thread.sleep(500);
            try (PreparedStatement ignored = conn1.prepareStatement("SELECT 1 FROM DUAL")) {
                assertNotNull(ignored);
            }
            Thread.sleep(800);
            try (Connection ignored = ds.getConnection()) {
                assertNotNull(ignored);
            }
            try (Statement ignored = conn1.createStatement()) {
                assertNotNull(ignored);
            }
        }
    }

    @Test
    public void testLastUsedLargePreparedStatementUse() throws Exception {
        ds.setRemoveAbandonedTimeout(1);
        ds.setMaxTotal(2);
        try (Connection conn1 = ds.getConnection();
             Statement st = conn1.createStatement()) {
            final String querySQL = "SELECT 1 FROM DUAL";
            Thread.sleep(500);
            assertNotNull(st.executeQuery(querySQL));
            Thread.sleep(800);
            final Connection conn2 = ds.getConnection();
            assertNotNull(st.executeQuery(querySQL));
            conn2.close();
            Thread.sleep(500);
            st.executeLargeUpdate("");
            Thread.sleep(800);
            try (Connection ignored = ds.getConnection()) {
                assertNotNull(ignored);
            }
            try (Statement ignored = conn1.createStatement()) {
                assertNotNull(ignored);
            }
        }
    }

    @Test
    public void testLastUsedPrepareCall() throws Exception {
        ds.setRemoveAbandonedTimeout(1);
        ds.setMaxTotal(2);
        try (Connection conn1 = ds.getConnection()) {
            Thread.sleep(500);
            try (CallableStatement ignored = conn1.prepareCall("{call home}")) {
                assertNotNull(ignored);
            }
            Thread.sleep(800);
            final Connection conn2 = ds.getConnection();
            try (CallableStatement ignored = conn1.prepareCall("{call home}")) {
                assertNotNull(ignored);
            }
            conn2.close();
            Thread.sleep(500);
            try (CallableStatement ignored = conn1.prepareCall("{call home}")) {
                assertNotNull(ignored);
            }
            Thread.sleep(800);
            try (Connection ignored = ds.getConnection()) {
                assertNotNull(ignored);
            }
            try (Statement ignored = conn1.createStatement()) {
                assertNotNull(ignored);
            }
        }
    }

    @Test
    public void testLastUsedPreparedStatementUse() throws Exception {
        ds.setRemoveAbandonedTimeout(1);
        ds.setMaxTotal(2);
        try (Connection conn1 = ds.getConnection(); Statement st = conn1.createStatement()) {
            final String querySQL = "SELECT 1 FROM DUAL";
            Thread.sleep(500);
            assertNotNull(st.executeQuery(querySQL));
            Thread.sleep(800);
            final Connection conn2 = ds.getConnection();
            assertNotNull(st.executeQuery(querySQL));
            conn2.close();
            Thread.sleep(500);
            st.executeUpdate("");
            Thread.sleep(800);
            try (Connection ignored = ds.getConnection()) {
                assertNotNull(ignored);
            }
            try (Statement ignored = conn1.createStatement()) {
                assertNotNull(ignored);
            }
        }
    }

    @Test
    public void testLastUsedUpdate() throws Exception {
        final DelegatingConnection<?> conn = (DelegatingConnection<?>) ds.getConnection();
        final PreparedStatement ps = conn.prepareStatement("");
        final CallableStatement cs = conn.prepareCall("");
        final Statement st = conn.prepareStatement("");
        checkLastUsedStatement(ps, conn);
        checkLastUsedPreparedStatement(ps, conn);
        checkLastUsedStatement(cs, conn);
        checkLastUsedPreparedStatement(cs, conn);
        checkLastUsedStatement(st, conn);
    }
}
