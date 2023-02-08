/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org_apache_commons.commons_dbcp2.cpdsadapter;

import org.apache.commons.dbcp2.Constants;
import org.apache.commons.dbcp2.cpdsadapter.DriverAdapterCPDS;
import org.apache.commons.dbcp2.datasources.SharedPoolDataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings({"ResultOfMethodCallIgnored", "EmptyTryBlock", "deprecation", "SqlDialectInspection", "SqlNoDataSourceInspection"})
public class TestDriverAdapterCPDS {
    private static class ThreadDbcp367 extends Thread {
        private final DataSource ds;
        private volatile boolean failed;

        ThreadDbcp367(final DataSource ds) {
            this.ds = ds;
        }

        public boolean isFailed() {
            return failed;
        }

        @Override
        public void run() {
            Connection c;
            try {
                for (int j = 0; j < 5000; j++) {
                    c = ds.getConnection();
                    c.close();
                }
            } catch (final SQLException sqle) {
                failed = true;
                sqle.printStackTrace();
            }
        }
    }

    private DriverAdapterCPDS pcds;

    @BeforeEach
    public void setUp() throws Exception {
        pcds = new DriverAdapterCPDS();
        pcds.setDriver("org.apache.commons.dbcp2.TesterDriver");
        pcds.setUrl("jdbc:apache:commons:testdriver");
        pcds.setUser("foo");
        pcds.setPassword("bar");
        pcds.setPoolPreparedStatements(false);
    }

    @Test
    public void testClosingWithUserName() throws Exception {
        final Connection[] c = new Connection[10];
        for (int i = 0; i < c.length; i++) {
            c[i] = pcds.getPooledConnection("u1", "p1").getConnection();
        }
        c[0].close();
        assertTrue(c[0].isClosed());
        c[0] = pcds.getPooledConnection("u1", "p1").getConnection();
        for (final Connection element : c) {
            element.close();
        }
        for (int i = 0; i < c.length; i++) {
            c[i] = pcds.getPooledConnection("u1", "p1").getConnection();
        }
        for (final Connection element : c) {
            element.close();
        }
    }

    @Test
    public void testDbcp367() throws Exception {
        final ThreadDbcp367[] threads = new ThreadDbcp367[200];
        pcds.setPoolPreparedStatements(true);
        pcds.setMaxPreparedStatements(-1);
        pcds.setAccessToUnderlyingConnectionAllowed(true);
        try (SharedPoolDataSource spds = new SharedPoolDataSource()) {
            spds.setConnectionPoolDataSource(pcds);
            spds.setMaxTotal(threads.length + 10);
            spds.setDefaultMaxWaitMillis(-1);
            spds.setDefaultMaxIdle(10);
            spds.setDefaultAutoCommit(Boolean.FALSE);
            spds.setValidationQuery("SELECT 1");
            spds.setDefaultTimeBetweenEvictionRunsMillis(10000);
            spds.setDefaultNumTestsPerEvictionRun(-1);
            spds.setDefaultTestWhileIdle(true);
            spds.setDefaultTestOnBorrow(true);
            spds.setDefaultTestOnReturn(false);
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new ThreadDbcp367(spds);
                threads[i].start();
            }
            for (int i = 0; i < threads.length; i++) {
                threads[i].join();
                Assertions.assertFalse(threads[i].isFailed(), "Thread " + i + " has failed");
            }
        }
    }

    @Test
    public void testGetObjectInstance() throws Exception {
        final Reference ref = pcds.getReference();
        final Object o = pcds.getObjectInstance(ref, null, null, null);
        assertEquals(pcds.getDriver(), ((DriverAdapterCPDS) o).getDriver());
    }

    @Test
    public void testGetObjectInstanceChangeDescription() throws Exception {
        final Reference ref = pcds.getReference();
        for (int i = 0; i < ref.size(); i++) {
            if (ref.get(i).getType().equals("description")) {
                ref.remove(i);
                break;
            }
        }
        ref.add(new StringRefAddr("description", "anything"));
        final Object o = pcds.getObjectInstance(ref, null, null, null);
        assertEquals(pcds.getDescription(), ((DriverAdapterCPDS) o).getDescription());
    }

    @Test
    public void testGetObjectInstanceNull() throws Exception {
        final Object o = pcds.getObjectInstance(null, null, null, null);
        assertNull(o);
    }

    @Test
    public void testGetParentLogger() {
        assertThrows(SQLFeatureNotSupportedException.class, pcds::getParentLogger);
    }

    @Test
    public void testGetReference() throws NamingException {
        final Reference ref = pcds.getReference();
        assertEquals(pcds.getDriver(), ref.get("driver").getContent());
        assertEquals(pcds.getDescription(), ref.get("description").getContent());
    }

    @Test
    public void testGettersAndSetters() {
        pcds.setUser("foo");
        assertEquals("foo", pcds.getUser());
        pcds.setPassword("bar");
        assertEquals("bar", pcds.getPassword());
        pcds.setPassword(new char[]{'a', 'b'});
        assertArrayEquals(new char[]{'a', 'b'}, pcds.getPasswordCharArray());
        final PrintWriter pw = new PrintWriter(System.err);
        pcds.setLogWriter(pw);
        assertEquals(pw, pcds.getLogWriter());
        pcds.setLoginTimeout(10);
        assertEquals(10, pcds.getLoginTimeout());
        pcds.setMaxIdle(100);
        assertEquals(100, pcds.getMaxIdle());
        pcds.setTimeBetweenEvictionRunsMillis(100);
        assertEquals(100, pcds.getTimeBetweenEvictionRunsMillis());
        pcds.setDurationBetweenEvictionRuns(Duration.ofMillis(100));
        assertEquals(100, pcds.getDurationBetweenEvictionRuns().toMillis());
        pcds.setNumTestsPerEvictionRun(1);
        assertEquals(1, pcds.getNumTestsPerEvictionRun());
        pcds.setMinEvictableIdleTimeMillis(11);
        assertEquals(11, pcds.getMinEvictableIdleTimeMillis());
        pcds.setMinEvictableIdleDuration(Duration.ofMillis(11));
        assertEquals(Duration.ofMillis(11), pcds.getMinEvictableIdleDuration());
        pcds.setDescription("jo");
        assertEquals("jo", pcds.getDescription());
    }

    @Test
    public void testIncorrectPassword() throws Exception {
        pcds.getPooledConnection("u2", "p2").close();
        try {
            pcds.getPooledConnection("u1", "zlsafjk");
            fail("Able to retrieve connection with incorrect password");
        } catch (final SQLException ignored) {
        }
        pcds.getPooledConnection("u1", "p1").close();
        try {
            pcds.getPooledConnection("u1", "x");
            fail("Able to retrieve connection with incorrect password");
        } catch (final SQLException e) {
            if (!e.getMessage().startsWith("x is not the correct password")) {
                throw e;
            }
        }
        pcds.getPooledConnection("u1", "p1").close();
    }

    @Test
    public void testNullValidationQuery() throws Exception {
        try (SharedPoolDataSource spds = new SharedPoolDataSource()) {
            spds.setConnectionPoolDataSource(pcds);
            spds.setDefaultTestOnBorrow(true);
            try (Connection ignored = spds.getConnection()) {
                assertNotNull(ignored);
            }
        }
    }

    @Test
    public void testSetConnectionProperties() throws Exception {
        pcds.setUser("bad");
        final Properties properties = new Properties();
        properties.put(Constants.KEY_USER, "foo");
        properties.put(Constants.KEY_PASSWORD, pcds.getPassword());
        pcds.setConnectionProperties(properties);
        pcds.getPooledConnection().close();
        assertEquals("foo", pcds.getUser());
        properties.put("password", "bad");
        assertEquals("bar", pcds.getPassword());
        pcds.getPooledConnection("foo", "bar").close();
        assertEquals("bar", pcds.getConnectionProperties().getProperty("password"));
    }

    @Test
    public void testSetConnectionPropertiesConnectionCalled() throws Exception {
        final Properties properties = new Properties();
        pcds.getPooledConnection().close();
        assertThrows(IllegalStateException.class, () -> pcds.setConnectionProperties(properties));
    }

    @Test
    public void testSetConnectionPropertiesNull() {
        pcds.setConnectionProperties(null);
    }

    @Test
    public void testSetPasswordNull() {
        pcds.setPassword("Secret");
        assertEquals("Secret", pcds.getPassword());
        pcds.setPassword((char[]) null);
        assertNull(pcds.getPassword());
    }

    @Test
    public void testSetPasswordNullWithConnectionProperties() {
        pcds.setConnectionProperties(new Properties());
        pcds.setPassword("Secret");
        assertEquals("Secret", pcds.getPassword());
        pcds.setPassword((char[]) null);
        assertNull(pcds.getPassword());
    }

    @Test
    public void testSetPasswordThenModCharArray() {
        final char[] pwd = {'a'};
        pcds.setPassword(pwd);
        assertEquals("a", pcds.getPassword());
        pwd[0] = 'b';
        assertEquals("a", pcds.getPassword());
    }

    @Test
    public void testSetUserNull() {
        pcds.setUser("Alice");
        assertEquals("Alice", pcds.getUser());
        pcds.setUser(null);
        assertNull(pcds.getUser());
    }

    @Test
    public void testSetUserNullWithConnectionProperties() {
        pcds.setConnectionProperties(new Properties());
        pcds.setUser("Alice");
        assertEquals("Alice", pcds.getUser());
        pcds.setUser(null);
        assertNull(pcds.getUser());
    }

    @Test
    public void testSimple() throws Exception {
        try (Connection conn = pcds.getPooledConnection().getConnection()) {
            assertNotNull(conn);
            try (PreparedStatement stmt = conn.prepareStatement("select * from dual")) {
                assertNotNull(stmt);
                try (ResultSet rset = stmt.executeQuery()) {
                    assertNotNull(rset);
                    assertTrue(rset.next());
                }
            }
        }
    }

    @Test
    public void testSimpleWithUsername() throws Exception {
        try (Connection conn = pcds.getPooledConnection("u1", "p1").getConnection()) {
            assertNotNull(conn);
            try (PreparedStatement stmt = conn.prepareStatement("select * from dual")) {
                assertNotNull(stmt);
                try (ResultSet rset = stmt.executeQuery()) {
                    assertNotNull(rset);
                    assertTrue(rset.next());
                }
            }
        }
    }

    @Test
    public void testToStringWithoutConnectionProperties() throws ClassNotFoundException {
        final DriverAdapterCPDS cleanCpds = new DriverAdapterCPDS();
        cleanCpds.setDriver("org.apache.commons.dbcp2.TesterDriver");
        cleanCpds.setUrl("jdbc:apache:commons:testdriver");
        cleanCpds.setUser("foo");
        cleanCpds.setPassword("bar");
        cleanCpds.toString();
    }
}
