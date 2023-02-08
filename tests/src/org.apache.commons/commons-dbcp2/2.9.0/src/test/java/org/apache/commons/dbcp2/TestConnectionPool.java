/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Hashtable;
import java.util.Random;
import java.util.Stack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection", "ThrowableNotThrown", "UnusedAssignment", "MagicConstant", "resource", "BusyWait"})
public abstract class TestConnectionPool {
    @SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection", "BusyWait", "UnusedAssignment", "unused"})
    protected class PoolTest implements Runnable {
        private final int connHoldTime;

        private final int numStatements;

        private volatile boolean isRun;

        private String state;
        private final Thread thread;
        private Throwable thrown;
        private final Random random = new Random();
        private final long createdMillis;
        private long started;
        private long ended;
        private long preconnected;
        private long connected;
        private long postconnected;
        private int loops;
        private int connHash;
        private final boolean stopOnException;
        private final boolean loopOnce;

        public PoolTest(final ThreadGroup threadGroup, final int connHoldTime, final boolean isStopOnException) {
            this(threadGroup, connHoldTime, isStopOnException, false, 1);
        }

        private PoolTest(final ThreadGroup threadGroup, final int connHoldTime, final boolean isStopOnException, final boolean once, final int numStatements) {
            this.loopOnce = once;
            this.connHoldTime = connHoldTime;
            stopOnException = isStopOnException;
            isRun = true;
            thrown = null;
            thread = new Thread(threadGroup, this, "Thread+" + currentThreadCount++);
            thread.setDaemon(false);
            createdMillis = timeStampMillis();
            this.numStatements = numStatements;
        }

        public PoolTest(final ThreadGroup threadGroup, final int connHoldTime, final boolean isStopOnException, final int numStatements) {
            this(threadGroup, connHoldTime, isStopOnException, false, numStatements);
        }

        public Thread getThread() {
            return thread;
        }

        @Override
        public void run() {
            started = timeStampMillis();
            try {
                while (isRun) {
                    loops++;
                    state = "Getting Connection";
                    preconnected = timeStampMillis();
                    final Connection conn = getConnection();
                    connHash = System.identityHashCode(((DelegatingConnection<?>) conn).getInnermostDelegate());
                    connected = timeStampMillis();
                    state = "Using Connection";
                    assertNotNull(conn);
                    final String sql = numStatements == 1 ? "select * from dual" : "select count " + random.nextInt(numStatements - 1);
                    final PreparedStatement stmt =
                            conn.prepareStatement(sql);
                    assertNotNull(stmt);
                    final ResultSet rset = stmt.executeQuery();
                    assertNotNull(rset);
                    assertTrue(rset.next());
                    state = "Holding Connection";
                    Thread.sleep(connHoldTime);
                    state = "Closing ResultSet";
                    rset.close();
                    state = "Closing Statement";
                    stmt.close();
                    state = "Closing Connection";
                    conn.close();
                    postconnected = timeStampMillis();
                    state = "Closed";
                    if (loopOnce) {
                        break;
                    }
                }
                state = DONE;
            } catch (final Throwable t) {
                thrown = t;
                if (!stopOnException) {
                    throw new RuntimeException();
                }
            } finally {
                ended = timeStampMillis();
            }
        }

        public void start() {
            thread.start();
        }

        public void stop() {
            isRun = false;
        }
    }

    @SuppressWarnings({"unused", "SqlDialectInspection", "SqlNoDataSourceInspection", "BusyWait", "UnusedAssignment"})
    class TestThread implements Runnable {
        final Random _random = new Random();
        boolean _complete;
        boolean _failed;
        int _iter = 100;
        int _delay = 50;

        TestThread() {
        }

        TestThread(final int iter) {
            _iter = iter;
        }

        TestThread(final int iter, final int delay) {
            _iter = iter;
            _delay = delay;
        }

        public boolean complete() {
            return _complete;
        }

        public boolean failed() {
            return _failed;
        }

        @Override
        public void run() {
            for (int i = 0; i < _iter; i++) {
                try {
                    Thread.sleep(_random.nextInt(_delay));
                } catch (final Exception e) {
                }
                try (Connection conn = newConnection();
                     PreparedStatement stmt = conn.prepareStatement("select 'literal', SYSDATE from dual");
                     ResultSet rset = stmt.executeQuery()) {
                    try {
                        Thread.sleep(_random.nextInt(_delay));
                    } catch (final Exception e) {
                    }
                } catch (final Exception e) {
                    e.printStackTrace();
                    _failed = true;
                    _complete = true;
                    break;
                }
            }
            _complete = true;
        }
    }

    private static final boolean DISPLAY_THREAD_DETAILS = Boolean.parseBoolean(System.getProperty("TestConnectionPool.display.thread.details", "false"));
    private static int currentThreadCount;
    private static final String DONE = "Done";
    //Checkstyle: stop field name check
    protected final Stack<Connection> connections = new Stack<>();
    //Checkstyle: resume field name check

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
        assertTrue(conn.isClosed());
        assertTrue(isClosed(statement));
        assertTrue(isClosed(resultSet));
        assertTrue(isClosed(executeResultSet));
        assertTrue(isClosed(keysResultSet));
        if (preparedResultSet != null) {
            assertTrue(isClosed(preparedResultSet));
        }
    }

    protected abstract Connection getConnection() throws Exception;

    protected int getMaxTotal() {
        return 10;
    }

    protected long getMaxWaitMillis() {
        return 100L;
    }

    protected String getUsername(final Connection conn) throws SQLException {
        final Statement stmt = conn.createStatement();
        final ResultSet rs = stmt.executeQuery("select username");
        if (rs.next()) {
            return rs.getString(1);
        }
        return null;
    }

    protected boolean isClosed(final ResultSet resultSet) {
        try {
            resultSet.getWarnings();
            return false;
        } catch (final SQLException e) {
            return true;
        }
    }

    protected boolean isClosed(final Statement statement) {
        try {
            statement.getWarnings();
            return false;
        } catch (final SQLException e) {
            return true;
        }
    }

    protected void multipleThreads(final int holdTime,
                                   final boolean expectError, final boolean loopOnce,
                                   final long maxWaitMillis) throws Exception {
        multipleThreads(holdTime, expectError, loopOnce, maxWaitMillis, 1, 2 * getMaxTotal(), 300);
    }

    protected void multipleThreads(final int holdTime, final boolean expectError, final boolean loopOnce,
                                   final long maxWaitMillis, final int numStatements, final int numThreads, final long duration) throws Exception {
        final long startTimeMillis = timeStampMillis();
        final PoolTest[] pts = new PoolTest[numThreads];
        final ThreadGroup threadGroup = new ThreadGroup("foo") {
            @Override
            public void uncaughtException(final Thread t, final Throwable e) {
                for (final PoolTest pt : pts) {
                    pt.stop();
                }
            }
        };
        for (int i = 0; i < pts.length; i++) {
            pts[i] = new PoolTest(threadGroup, holdTime, expectError, loopOnce, numStatements);
        }
        for (final PoolTest pt : pts) {
            pt.start();
        }
        Thread.sleep(duration);
        for (final PoolTest pt : pts) {
            pt.stop();
        }
        int done = 0;
        int failed = 0;
        int didNotRun = 0;
        int loops = 0;
        for (final PoolTest poolTest : pts) {
            poolTest.thread.join();
            loops += poolTest.loops;
            final String state = poolTest.state;
            if (DONE.equals(state)) {
                done++;
            }
            if (poolTest.loops == 0) {
                didNotRun++;
            }
            final Throwable thrown = poolTest.thrown;
            if (thrown != null) {
                failed++;
                if (!expectError || !(thrown instanceof SQLException)) {
                    System.err.println("Unexpected error: " + thrown.getMessage());
                }
            }
        }

        final long timeMillis = timeStampMillis() - startTimeMillis;
        println("Multithread test time = " + timeMillis
                + " ms. Threads: " + pts.length
                + ". Loops: " + loops
                + ". Hold time: " + holdTime
                + ". maxWaitMillis: " + maxWaitMillis
                + ". Done: " + done
                + ". Did not run: " + didNotRun
                + ". Failed: " + failed
                + ". expectError: " + expectError
        );
        if (expectError) {
            if (DISPLAY_THREAD_DETAILS || pts.length / 2 != failed) {
                final long offset = pts[0].createdMillis - 1000;
                println("Offset: " + offset);
                for (int i = 0; i < pts.length; i++) {
                    final PoolTest pt = pts[i];
                    println(
                            "Pre: " + (pt.preconnected - offset)
                                    + ". Post: " + (pt.postconnected != 0 ? Long.toString(pt.postconnected - offset) : "-")
                                    + ". Hash: " + pt.connHash
                                    + ". Startup: " + (pt.started - pt.createdMillis)
                                    + ". getConn(): " + (pt.connected != 0 ? Long.toString(pt.connected - pt.preconnected) : "-")
                                    + ". Runtime: " + (pt.ended - pt.started)
                                    + ". IDX: " + i
                                    + ". Loops: " + pt.loops
                                    + ". State: " + pt.state
                                    + ". thrown: " + pt.thrown
                                    + "."
                    );
                }
            }
            if (didNotRun > 0) {
                println("NOTE: some threads did not run the code: " + didNotRun);
            }
            assertTrue(failed > 0, "Expected some of the threads to fail");
            assertEquals(pts.length / 2, failed + didNotRun, "WARNING: Expected half the threads to fail");
        } else {
            assertEquals(0, failed, "Did not expect any threads to fail");
        }
    }

    protected Connection newConnection() throws Exception {
        final Connection connection = getConnection();
        connections.push(connection);
        return connection;
    }

    void println(final String string) {
        if (Boolean.getBoolean(getClass().getSimpleName() + ".debug")) {
            System.out.println(string);
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        while (!connections.isEmpty()) {
            Connection conn = connections.pop();
            try {
                conn.close();
            } catch (final Exception ex) {
            } finally {
                conn = null;
            }
        }
    }

    @Test
    public void testAutoCommitBehavior() throws Exception {
        final Connection conn0 = newConnection();
        assertNotNull(conn0, "connection should not be null");
        assertTrue(conn0.getAutoCommit(), "autocommit should be true for conn0");
        final Connection conn1 = newConnection();
        assertTrue(conn1.getAutoCommit(), "autocommit should be true for conn1");
        conn1.close();
        assertTrue(conn0.getAutoCommit(), "autocommit should be true for conn0");
        conn0.setAutoCommit(false);
        assertFalse(conn0.getAutoCommit(), "autocommit should be false for conn0");
        conn0.close();
        final Connection conn2 = newConnection();
        assertTrue(conn2.getAutoCommit(), "autocommit should be true for conn2");
        final Connection conn3 = newConnection();
        assertTrue(conn3.getAutoCommit(), "autocommit should be true for conn3");
        conn2.close();
        conn3.close();
    }

    @Test
    public void testBackPointers() throws Exception {
        Connection conn = newConnection();
        assertBackPointers(conn, conn.createStatement());
        conn = newConnection();
        assertBackPointers(conn, conn.createStatement(0, 0));
        conn = newConnection();
        assertBackPointers(conn, conn.createStatement(0, 0, 0));
        conn = newConnection();
        assertBackPointers(conn, conn.prepareStatement("select * from dual"));
        conn = newConnection();
        assertBackPointers(conn, conn.prepareStatement("select * from dual", 0));
        conn = newConnection();
        assertBackPointers(conn, conn.prepareStatement("select * from dual", 0, 0));
        conn = newConnection();
        assertBackPointers(conn, conn.prepareStatement("select * from dual", 0, 0, 0));
        conn = newConnection();
        assertBackPointers(conn, conn.prepareStatement("select * from dual", new int[0]));
        conn = newConnection();
        assertBackPointers(conn, conn.prepareStatement("select * from dual", new String[0]));
        conn = newConnection();
        assertBackPointers(conn, conn.prepareCall("select * from dual"));
        conn = newConnection();
        assertBackPointers(conn, conn.prepareCall("select * from dual", 0, 0));
        conn = newConnection();
        assertBackPointers(conn, conn.prepareCall("select * from dual", 0, 0, 0));
    }

    @Test
    public void testCanCloseCallableStatementTwice() throws Exception {
        final Connection conn = newConnection();
        assertNotNull(conn);
        assertFalse(conn.isClosed());
        for (int i = 0; i < 2; i++) {
            final PreparedStatement stmt = conn.prepareCall("select * from dual");
            assertNotNull(stmt);
            assertFalse(isClosed(stmt));
            stmt.close();
            assertTrue(isClosed(stmt));
            stmt.close();
            assertTrue(isClosed(stmt));
            stmt.close();
            assertTrue(isClosed(stmt));
        }
        conn.close();
    }

    @Test
    public void testCanCloseConnectionTwice() throws Exception {
        for (int i = 0; i < getMaxTotal(); i++) {
            final Connection conn = newConnection();
            assertNotNull(conn);
            assertFalse(conn.isClosed());
            conn.close();
            assertTrue(conn.isClosed());
            conn.close();
            assertTrue(conn.isClosed());
        }
    }

    @Test
    public void testCanClosePreparedStatementTwice() throws Exception {
        final Connection conn = newConnection();
        assertNotNull(conn);
        assertFalse(conn.isClosed());
        for (int i = 0; i < 2; i++) {
            final PreparedStatement stmt = conn.prepareStatement("select * from dual");
            assertNotNull(stmt);
            assertFalse(isClosed(stmt));
            stmt.close();
            assertTrue(isClosed(stmt));
            stmt.close();
            assertTrue(isClosed(stmt));
            stmt.close();
            assertTrue(isClosed(stmt));
        }
        conn.close();
    }

    @Test
    public void testCanCloseResultSetTwice() throws Exception {
        final Connection conn = newConnection();
        assertNotNull(conn);
        assertFalse(conn.isClosed());
        for (int i = 0; i < 2; i++) {
            final PreparedStatement stmt = conn.prepareStatement("select * from dual");
            assertNotNull(stmt);
            final ResultSet rset = stmt.executeQuery();
            assertNotNull(rset);
            assertFalse(isClosed(rset));
            rset.close();
            assertTrue(isClosed(rset));
            rset.close();
            assertTrue(isClosed(rset));
            rset.close();
            assertTrue(isClosed(rset));
        }
        conn.close();
    }

    @Test
    public void testCanCloseStatementTwice() throws Exception {
        final Connection conn = newConnection();
        assertNotNull(conn);
        assertFalse(conn.isClosed());
        for (int i = 0; i < 2; i++) {
            final Statement stmt = conn.createStatement();
            assertNotNull(stmt);
            assertFalse(isClosed(stmt));
            stmt.close();
            assertTrue(isClosed(stmt));
            stmt.close();
            assertTrue(isClosed(stmt));
            stmt.close();
            assertTrue(isClosed(stmt));
        }
        conn.close();
    }

    @Test
    public void testClearWarnings() throws Exception {
        final Connection[] c = new Connection[getMaxTotal()];
        for (int i = 0; i < c.length; i++) {
            c[i] = newConnection();
            assertNotNull(c[i]);
            try (CallableStatement ignored = c[i].prepareCall("warning")) {
                assertNotNull(ignored);
            }
        }
        for (final Connection element : c) {
            assertNotNull(element.getWarnings());
        }
        for (final Connection element : c) {
            element.close();
        }
        for (int i = 0; i < c.length; i++) {
            c[i] = newConnection();
        }
        for (final Connection element : c) {
            assertNull(element.getWarnings());
        }
        for (final Connection element : c) {
            element.close();
        }
    }

    @Test
    public void testClosing() throws Exception {
        final Connection[] c = new Connection[getMaxTotal()];
        for (int i = 0; i < c.length; i++) {
            c[i] = newConnection();
        }
        c[0].close();
        assertTrue(c[0].isClosed());
        c[0] = newConnection();
        for (final Connection element : c) {
            element.close();
        }
    }

    @Test
    public void testConnectionsAreDistinct() throws Exception {
        final Connection[] conn = new Connection[getMaxTotal()];
        for (int i = 0; i < conn.length; i++) {
            conn[i] = newConnection();
            for (int j = 0; j < i; j++) {
                assertNotSame(conn[j], conn[i]);
                assertNotEquals(conn[j], conn[i]);
            }
        }
        for (final Connection element : conn) {
            element.close();
        }
    }

    @Test
    public void testHashCode() throws Exception {
        final Connection conn1 = newConnection();
        assertNotNull(conn1);
        final Connection conn2 = newConnection();
        assertNotNull(conn2);
        assertTrue(conn1.hashCode() != conn2.hashCode());
    }

    @Test
    public void testHashing() throws Exception {
        final Connection con = getConnection();
        //Checkstyle: stop field name check
        final Hashtable<Connection, String> hash = new Hashtable<>();
        //Checkstyle: resume field name check
        hash.put(con, "test");
        assertEquals("test", hash.get(con));
        assertTrue(hash.containsKey(con));
        assertTrue(hash.contains("test"));
        hash.clear();
        con.close();
    }

    @Test
    public void testIsClosed() throws Exception {
        for (int i = 0; i < getMaxTotal(); i++) {
            final Connection conn = newConnection();
            assertNotNull(conn);
            assertFalse(conn.isClosed());
            final PreparedStatement stmt = conn.prepareStatement("select * from dual");
            assertNotNull(stmt);
            final ResultSet rset = stmt.executeQuery();
            assertNotNull(rset);
            assertTrue(rset.next());
            rset.close();
            stmt.close();
            conn.close();
            assertTrue(conn.isClosed());
        }
    }

    @Test
    public void testMaxTotal() throws Exception {
        final Connection[] c = new Connection[getMaxTotal()];
        for (int i = 0; i < c.length; i++) {
            c[i] = newConnection();
            assertNotNull(c[i]);
        }
        try {
            newConnection();
            fail("Allowed to open more than DefaultMaxTotal connections.");
        } catch (final SQLException e) {
        }
        for (final Connection element : c) {
            element.close();
        }
    }

    @Test
    public void testNoRsetClose() throws Exception {
        final Connection conn = newConnection();
        assertNotNull(conn);
        final PreparedStatement stmt = conn.prepareStatement("test");
        assertNotNull(stmt);
        final ResultSet rset = stmt.getResultSet();
        assertNotNull(rset);
        stmt.close();
        conn.close();
    }

    @Test
    public void testOpening() throws Exception {
        final Connection[] c = new Connection[getMaxTotal()];
        for (int i = 0; i < c.length; i++) {
            c[i] = newConnection();
            assertNotNull(c[i]);
            for (int j = 0; j <= i; j++) {
                assertFalse(c[j].isClosed());
            }
        }

        for (final Connection element : c) {
            element.close();
        }
    }

    @Test
    public void testPooling() throws Exception {
        final Connection[] c = new Connection[getMaxTotal()];
        final Connection[] u = new Connection[getMaxTotal()];
        for (int i = 0; i < c.length; i++) {
            c[i] = newConnection();
            if (!(c[i] instanceof DelegatingConnection)) {
                for (int j = 0; j <= i; j++) {
                    c[j].close();
                }
                return;
            }
            u[i] = ((DelegatingConnection<?>) c[i]).getInnermostDelegate();
        }
        for (final Connection element : c) {
            element.close();
            final Connection con = newConnection();
            final Connection underCon =
                    ((DelegatingConnection<?>) con).getInnermostDelegate();
            assertNotNull(underCon, "Failed to get connection");
            boolean found = false;
            for (int j = 0; j < c.length; j++) {
                if (underCon == u[j]) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "New connection not from pool");
            con.close();
        }
    }

    @Test
    public void testPrepareStatementOptions() throws Exception {
        final Connection conn = newConnection();
        assertNotNull(conn);
        final PreparedStatement stmt = conn.prepareStatement("select * from dual", ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
        assertNotNull(stmt);
        final ResultSet rset = stmt.executeQuery();
        assertNotNull(rset);
        assertTrue(rset.next());
        assertEquals(ResultSet.TYPE_SCROLL_SENSITIVE, rset.getType());
        assertEquals(ResultSet.CONCUR_UPDATABLE, rset.getConcurrency());
        rset.close();
        stmt.close();
        conn.close();
    }

    @Test
    public void testRepeatedBorrowAndReturn() throws Exception {
        for (int i = 0; i < 100; i++) {
            final Connection conn = newConnection();
            assertNotNull(conn);
            final PreparedStatement stmt = conn.prepareStatement("select * from dual");
            assertNotNull(stmt);
            final ResultSet rset = stmt.executeQuery();
            assertNotNull(rset);
            assertTrue(rset.next());
            rset.close();
            stmt.close();
            conn.close();
        }
    }

    @Test
    public void testSimple() throws Exception {
        final Connection conn = newConnection();
        assertNotNull(conn);
        final PreparedStatement stmt = conn.prepareStatement("select * from dual");
        assertNotNull(stmt);
        final ResultSet rset = stmt.executeQuery();
        assertNotNull(rset);
        assertTrue(rset.next());
        rset.close();
        stmt.close();
        conn.close();
    }

    @Test
    public void testSimple2() throws Exception {
        Connection conn = newConnection();
        assertNotNull(conn);
        final PreparedStatement stmt1 = conn.prepareStatement("select * from dual");
        assertNotNull(stmt1);
        final ResultSet rset1 = stmt1.executeQuery();
        assertNotNull(rset1);
        assertTrue(rset1.next());
        rset1.close();
        stmt1.close();
        final PreparedStatement stmt2 = conn.prepareStatement("select * from dual");
        assertNotNull(stmt2);
        final ResultSet rset2 = stmt2.executeQuery();
        assertNotNull(rset2);
        assertTrue(rset2.next());
        rset2.close();
        stmt2.close();
        conn.close();
        try (Statement ignored = conn.createStatement()) {
            fail("Can't use closed connections");
        } catch (final SQLException e) {
        }
        conn = newConnection();
        assertNotNull(conn);
        final PreparedStatement stmt3 = conn.prepareStatement("select * from dual");
        assertNotNull(stmt3);
        final ResultSet rset3 = stmt3.executeQuery();
        assertNotNull(rset3);
        assertTrue(rset3.next());
        rset3.close();
        stmt3.close();
        final PreparedStatement stmt4 = conn.prepareStatement("select * from dual");
        assertNotNull(stmt4);
        final ResultSet rset4 = stmt4.executeQuery();
        assertNotNull(rset4);
        assertTrue(rset4.next());
        rset4.close();
        stmt4.close();
        conn.close();
        conn = null;
    }

    @Test
    public void testThreaded() {
        final TestThread[] threads = new TestThread[getMaxTotal()];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new TestThread(50, 50);
            final Thread t = new Thread(threads[i]);
            t.start();
        }
        for (int i = 0; i < threads.length; i++) {
            while (!threads[i].complete()) {
                try {
                    Thread.sleep(100L);
                } catch (final Exception e) {
                }
            }
            if (threads[i] != null && threads[i].failed()) {
                fail("Thread failed: " + i);
            }
        }
    }

    long timeStampMillis() {
        return System.currentTimeMillis();
    }
}
