/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.apache.commons.dbcp2.datasources;

import org.apache.commons.dbcp2.TestConnectionPool;
import org.apache.commons.dbcp2.TesterDriver;
import org.apache.commons.dbcp2.cpdsadapter.DriverAdapterCPDS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings({"deprecation", "SqlDialectInspection", "SqlNoDataSourceInspection", "UnusedAssignment"})
public class TestPerUserPoolDataSource extends TestConnectionPool {

    private String user;

    private DataSource ds;

    @Override
    protected Connection getConnection() throws SQLException {
        return ds.getConnection(user, "bar");
    }

    @BeforeEach
    public void setUp() throws Exception {
        user = "foo";
        final DriverAdapterCPDS pcds = new DriverAdapterCPDS();
        pcds.setDriver("org.apache.commons.dbcp2.TesterDriver");
        pcds.setUrl("jdbc:apache:commons:testdriver");
        pcds.setUser(user);
        pcds.setPassword("bar");
        pcds.setAccessToUnderlyingConnectionAllowed(true);

        final PerUserPoolDataSource tds = new PerUserPoolDataSource();
        tds.setConnectionPoolDataSource(pcds);
        tds.setDefaultMaxTotal(getMaxTotal());
        tds.setDefaultMaxWaitMillis((int) getMaxWaitMillis());
        tds.setPerUserMaxTotal(user, getMaxTotal());
        tds.setPerUserMaxWaitMillis(user, getMaxWaitMillis());
        tds.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        tds.setDefaultAutoCommit(Boolean.TRUE);
        ds = tds;
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        ((PerUserPoolDataSource) ds).close();
    }

    @Test
    public void testChangePassword() throws Exception {
        try (Connection ignored1 = ds.getConnection(user, "bay")) {
            fail("Should have generated SQLException");
        } catch (final SQLException expected) {
        }
        final Connection con1 = ds.getConnection(user, "bar");
        final Connection con2 = ds.getConnection(user, "bar");
        final Connection con3 = ds.getConnection(user, "bar");
        con1.close();
        con2.close();
        TesterDriver.addUser(user, "bay");
        try {
            final Connection con4 = ds.getConnection(user, "bay");
            assertEquals(0, ((PerUserPoolDataSource) ds).getNumIdle(user), "Should be no idle connections in the pool");
            con4.close();
            assertEquals(1, ((PerUserPoolDataSource) ds).getNumIdle(user), "Should be one idle connection in the pool");
            try (Connection ignored = ds.getConnection(user, "bar")) {
                fail("Should have generated SQLException");
            } catch (final SQLException expected) {
            }
            final Connection con5 = ds.getConnection(user, "bay");
            con3.close();
            ds.getConnection(user, "bay").close();
            assertEquals(1, ((PerUserPoolDataSource) ds).getNumIdle(user),
                    "Should be one idle connection in the pool");
            con5.close();
        } finally {
            TesterDriver.addUser(user, "bar");
        }
    }

    @Override
    @Test
    public void testClosing() throws Exception {
        final Connection[] c = new Connection[getMaxTotal()];
        for (int i = 0; i < c.length; i++) {
            c[i] = ds.getConnection();
        }
        c[0].close();
        assertTrue(c[0].isClosed());
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
        assertTrue(c[0].isClosed());
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
    public void testDefaultUser1() throws Exception {
        TesterDriver.addUser("mkh", "password");
        TesterDriver.addUser("hanafey", "password");
        TesterDriver.addUser("jsmith", "password");
        final PerUserPoolDataSource puds = (PerUserPoolDataSource) ds;
        puds.setPerUserMaxTotal("jsmith", 2);
        final String[] users = {"mkh", "hanafey", "jsmith"};
        final String password = "password";
        final Connection[] c = new Connection[users.length];
        for (int i = 0; i < users.length; i++) {
            c[i] = puds.getConnection(users[i], password);
            assertEquals(users[i], getUsername(c[i]));
        }
        for (int i = 0; i < users.length; i++) {
            c[i].close();
        }
    }

    @Test
    public void testDefaultUser2() throws Exception {
        TesterDriver.addUser("mkh", "password");
        TesterDriver.addUser("hanafey", "password");
        TesterDriver.addUser("jsmith", "password");
        final PerUserPoolDataSource puds = (PerUserPoolDataSource) ds;
        puds.setPerUserMaxTotal("jsmith", 2);
        final String[] users = {"jsmith", "hanafey", "mkh"};
        final String password = "password";
        final Connection[] c = new Connection[users.length];
        for (int i = 0; i < users.length; i++) {
            c[i] = puds.getConnection(users[i], password);
            assertEquals(users[i], getUsername(c[i]));
        }
        for (int i = 0; i < users.length; i++) {
            c[i].close();
        }
    }

    @Test
    public void testIncorrectPassword() throws Exception {
        try (Connection ignored = ds.getConnection("u1", "zlsafjk")) {
            fail("Able to retrieve connection with incorrect password");
        } catch (final SQLException e1) {
        }
        ds.getConnection("u1", "p1").close();
        try (Connection ignored1 = ds.getConnection("u1", "x")) {
            fail("Able to retrieve connection with incorrect password");
        } catch (final SQLException e) {
            if (!e.getMessage().startsWith("Given password did not match")) {
                throw e;
            }
        }
        ds.getConnection("u1", "p1").close();
        ds.getConnection(user, "bar").close();
        try (Connection ignored = ds.getConnection("foob", "ar")) {
            fail("Should have caused an SQLException");
        } catch (final SQLException expected) {
        }
        try (Connection ignored = ds.getConnection(user, "baz")) {
            fail("Should have generated SQLException");
        } catch (final SQLException expected) {
        }
    }

    @Override
    @Test
    public void testMaxTotal() throws Exception {
        final Connection[] c = new Connection[getMaxTotal()];
        for (int i = 0; i < c.length; i++) {
            c[i] = ds.getConnection();
            assertNotNull(c[i]);
        }
        try (Connection ignored = ds.getConnection()) {
            fail("Allowed to open more than DefaultMaxTotal connections.");
        } catch (final SQLException e) {
        }
        for (final Connection element : c) {
            element.close();
        }
    }

    @Test
    public void testMaxWaitMillisZero() throws Exception {
        final PerUserPoolDataSource tds = (PerUserPoolDataSource) ds;
        tds.setDefaultMaxWaitMillis(0);
        tds.setPerUserMaxTotal("u1", 1);
        final Connection conn = tds.getConnection("u1", "p1");
        try (Connection ignored = tds.getConnection("u1", "p1")) {
            fail("Expecting Pool Exhausted exception");
        } catch (final SQLException ex) {
        }
        conn.close();
    }

    @Test
    public void testMultipleThreads1() throws Exception {
        final int defaultMaxWaitMillis = 430;
        ((PerUserPoolDataSource) ds).setDefaultMaxWaitMillis(defaultMaxWaitMillis);
        ((PerUserPoolDataSource) ds).setPerUserMaxWaitMillis(user, (long) defaultMaxWaitMillis);
        multipleThreads(1, false, false, defaultMaxWaitMillis);
    }

    @Test
    public void testMultipleThreads2() throws Exception {
        final int defaultMaxWaitMillis = 500;
        ((PerUserPoolDataSource) ds).setDefaultMaxWaitMillis(defaultMaxWaitMillis);
        ((PerUserPoolDataSource) ds).setPerUserMaxWaitMillis(user, (long) defaultMaxWaitMillis);
        multipleThreads(2 * defaultMaxWaitMillis, true, true, defaultMaxWaitMillis);
    }

    @Override
    @Test
    public void testOpening() throws Exception {
        final Connection[] c = new Connection[getMaxTotal()];
        for (int i = 0; i < c.length; i++) {
            c[i] = ds.getConnection();
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
    public void testPerUserBlockWhenExhaustedMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        Map<String, Boolean> userDefaultBlockWhenExhausted = new HashMap<>();
        userDefaultBlockWhenExhausted.put("key", Boolean.FALSE);
        ds.setPerUserBlockWhenExhausted(userDefaultBlockWhenExhausted);
        assertEquals(Boolean.FALSE, ds.getPerUserBlockWhenExhausted("key"));
        userDefaultBlockWhenExhausted = new HashMap<>();
        userDefaultBlockWhenExhausted.put("anonymous", Boolean.FALSE);
        ds.setPerUserBlockWhenExhausted(userDefaultBlockWhenExhausted);
        assertEquals(Boolean.TRUE, ds.getPerUserBlockWhenExhausted("key"));
        assertEquals(Boolean.FALSE, ds.getPerUserBlockWhenExhausted("anonymous"));
    }

    @Test
    public void testPerUserBlockWhenExhaustedMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Boolean> userDefaultBlockWhenExhausted = new HashMap<>();
        userDefaultBlockWhenExhausted.put("key", Boolean.TRUE);
        ds.setPerUserBlockWhenExhausted(userDefaultBlockWhenExhausted);
        assertEquals(Boolean.TRUE, ds.getPerUserBlockWhenExhausted("key"));
    }

    @Test
    public void testPerUserBlockWhenExhaustedMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Boolean> userDefaultBlockWhenExhausted = new HashMap<>();
        userDefaultBlockWhenExhausted.put("key", Boolean.FALSE);
        ds.setPerUserBlockWhenExhausted(userDefaultBlockWhenExhausted);
        assertEquals(ds.getDefaultBlockWhenExhausted(), ds.getPerUserBlockWhenExhausted("missingkey"));
    }

    @Test
    public void testPerUserBlockWhenExhaustedWithUserMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserBlockWhenExhausted(user, Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserBlockWhenExhausted(user));
        ds.setPerUserBlockWhenExhausted("anotheruser", Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserBlockWhenExhausted(user));
        assertEquals(Boolean.FALSE, ds.getPerUserBlockWhenExhausted("anotheruser"));
    }

    @Test
    public void testPerUserBlockWhenExhaustedWithUserMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserBlockWhenExhausted(user, Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserBlockWhenExhausted(user));
    }

    @Test
    public void testPerUserBlockWhenExhaustedWithUserMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserBlockWhenExhausted("whatismyuseragain?", Boolean.FALSE);
        assertEquals(Boolean.TRUE, ds.getPerUserBlockWhenExhausted("missingkey"));
    }

    @Test
    public void testPerUserDefaultAutoCommitMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.FALSE);
        ds.setPerUserDefaultAutoCommit(values);
        assertEquals(Boolean.FALSE, ds.getPerUserDefaultAutoCommit("key"));
        values = new HashMap<>();
        values.put("anonymous", Boolean.FALSE);
        ds.setPerUserDefaultAutoCommit(values);
        assertNull(ds.getPerUserDefaultAutoCommit("key"));
        assertEquals(Boolean.FALSE, ds.getPerUserDefaultAutoCommit("anonymous"));
    }

    @Test
    public void testPerUserDefaultAutoCommitMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.TRUE);
        ds.setPerUserDefaultAutoCommit(values);
        assertEquals(Boolean.TRUE, ds.getPerUserDefaultAutoCommit("key"));
    }

    @Test
    public void testPerUserDefaultAutoCommitMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.FALSE);
        ds.setPerUserDefaultAutoCommit(values);
        assertNull(ds.getPerUserDefaultAutoCommit("missingkey"));
    }

    @Test
    public void testPerUserDefaultAutoCommitWithUserMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserDefaultAutoCommit(user, Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserDefaultAutoCommit(user));
        ds.setPerUserDefaultAutoCommit("anotheruser", Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserDefaultAutoCommit(user));
        assertEquals(Boolean.FALSE, ds.getPerUserDefaultAutoCommit("anotheruser"));
    }

    @Test
    public void testPerUserDefaultAutoCommitWithUserMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserDefaultAutoCommit(user, Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserDefaultAutoCommit(user));
    }

    @Test
    public void testPerUserDefaultAutoCommitWithUserMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserDefaultAutoCommit("whatismyuseragain?", Boolean.FALSE);
        assertNull(ds.getPerUserDefaultAutoCommit("missingkey"));
    }

    @Test
    public void testPerUserDefaultReadOnlyMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.FALSE);
        ds.setPerUserDefaultReadOnly(values);
        assertEquals(Boolean.FALSE, ds.getPerUserDefaultReadOnly("key"));
        values = new HashMap<>();
        values.put("anonymous", Boolean.FALSE);
        ds.setPerUserDefaultReadOnly(values);
        assertNull(ds.getPerUserDefaultReadOnly("key"));
        assertEquals(Boolean.FALSE, ds.getPerUserDefaultReadOnly("anonymous"));
    }

    @Test
    public void testPerUserDefaultReadOnlyMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.TRUE);
        ds.setPerUserDefaultReadOnly(values);
        assertEquals(Boolean.TRUE, ds.getPerUserDefaultReadOnly("key"));
    }

    @Test
    public void testPerUserDefaultReadOnlyMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.FALSE);
        ds.setPerUserDefaultReadOnly(values);
        assertNull(ds.getPerUserDefaultReadOnly("missingkey"));
    }

    @Test
    public void testPerUserDefaultReadOnlyWithUserMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserDefaultReadOnly(user, Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserDefaultReadOnly(user));
        ds.setPerUserDefaultReadOnly("anotheruser", Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserDefaultReadOnly(user));
        assertEquals(Boolean.FALSE, ds.getPerUserDefaultReadOnly("anotheruser"));
    }

    @Test
    public void testPerUserDefaultReadOnlyWithUserMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserDefaultReadOnly(user, Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserDefaultReadOnly(user));
    }

    @Test
    public void testPerUserDefaultReadOnlyWithUserMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserDefaultReadOnly("whatismyuseragain?", Boolean.FALSE);
        assertNull(ds.getPerUserDefaultReadOnly("missingkey"));
    }

    @Test
    public void testPerUserDefaultTransactionIsolationMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        Map<String, Integer> values = new HashMap<>();
        values.put("key", 0);
        ds.setPerUserDefaultTransactionIsolation(values);
        assertEquals((Integer) 0, ds.getPerUserDefaultTransactionIsolation("key"));
        values = new HashMap<>();
        values.put("anonymous", 0);
        ds.setPerUserDefaultTransactionIsolation(values);
        assertNull(ds.getPerUserDefaultTransactionIsolation("key"));
        assertEquals((Integer) 0, ds.getPerUserDefaultTransactionIsolation("anonymous"));
    }

    @Test
    public void testPerUserDefaultTransactionIsolationMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Integer> values = new HashMap<>();
        values.put("key", 1);
        ds.setPerUserDefaultTransactionIsolation(values);
        assertEquals((Integer) 1, ds.getPerUserDefaultTransactionIsolation("key"));
    }

    @Test
    public void testPerUserDefaultTransactionIsolationMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Integer> values = new HashMap<>();
        values.put("key", 0);
        ds.setPerUserDefaultTransactionIsolation(values);
        assertNull(ds.getPerUserDefaultTransactionIsolation("missingkey"));
    }

    @Test
    public void testPerUserDefaultTransactionIsolationWithUserMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserDefaultTransactionIsolation(user, 0);
        assertEquals((Integer) 0, ds.getPerUserDefaultTransactionIsolation(user));
        ds.setPerUserDefaultTransactionIsolation("anotheruser", 0);
        assertEquals((Integer) 0, ds.getPerUserDefaultTransactionIsolation(user));
        assertEquals((Integer) 0, ds.getPerUserDefaultTransactionIsolation("anotheruser"));
    }

    @Test
    public void testPerUserDefaultTransactionIsolationWithUserMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserDefaultTransactionIsolation(user, 0);
        assertEquals((Integer) 0, ds.getPerUserDefaultTransactionIsolation(user));
    }

    @Test
    public void testPerUserDefaultTransactionIsolationWithUserMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserDefaultTransactionIsolation("whatismyuseragain?", 0);
        assertNull(ds.getPerUserDefaultTransactionIsolation("missingkey"));
    }

    @Test
    public void testPerUserEvictionPolicyClassNameMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        Map<String, String> values = new HashMap<>();
        values.put("key", "bar");
        ds.setPerUserEvictionPolicyClassName(values);
        assertEquals("bar", ds.getPerUserEvictionPolicyClassName("key"));
        values = new HashMap<>();
        values.put("anonymous", "bar");
        ds.setPerUserEvictionPolicyClassName(values);
        assertEquals(ds.getDefaultEvictionPolicyClassName(), ds.getPerUserEvictionPolicyClassName("key"));
        assertEquals("bar", ds.getPerUserEvictionPolicyClassName("anonymous"));
    }

    @Test
    public void testPerUserEvictionPolicyClassNameMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, String> values = new HashMap<>();
        values.put("key", "test");
        ds.setPerUserEvictionPolicyClassName(values);
        assertEquals("test", ds.getPerUserEvictionPolicyClassName("key"));
    }

    @Test
    public void testPerUserEvictionPolicyClassNameMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, String> values = new HashMap<>();
        values.put("key", "bar");
        ds.setPerUserEvictionPolicyClassName(values);
        assertEquals(ds.getDefaultEvictionPolicyClassName(), ds.getPerUserEvictionPolicyClassName("missingkey"));
    }

    @Test
    public void testPerUserEvictionPolicyClassNameWithUserMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserEvictionPolicyClassName(user, "bar");
        assertEquals("bar", ds.getPerUserEvictionPolicyClassName(user));
        ds.setPerUserEvictionPolicyClassName("anotheruser", "bar");
        assertEquals("bar", ds.getPerUserEvictionPolicyClassName(user));
        assertEquals("bar", ds.getPerUserEvictionPolicyClassName("anotheruser"));
    }

    @Test
    public void testPerUserEvictionPolicyClassNameWithUserMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserEvictionPolicyClassName(user, "bar");
        assertEquals("bar", ds.getPerUserEvictionPolicyClassName(user));
    }

    @Test
    public void testPerUserEvictionPolicyClassNameWithUserMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserEvictionPolicyClassName("whatismyuseragain?", "bar");
        assertEquals(ds.getDefaultEvictionPolicyClassName(), ds.getPerUserEvictionPolicyClassName("missingkey"));
    }

    @Test
    public void testPerUserLifoMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.FALSE);
        ds.setPerUserLifo(values);
        assertEquals(Boolean.FALSE, ds.getPerUserLifo("key"));
        values = new HashMap<>();
        values.put("anonymous", Boolean.FALSE);
        ds.setPerUserLifo(values);
        assertEquals(ds.getDefaultLifo(), ds.getPerUserLifo("key"));
        assertEquals(Boolean.FALSE, ds.getPerUserLifo("anonymous"));
    }

    @Test
    public void testPerUserLifoMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.TRUE);
        ds.setPerUserLifo(values);
        assertEquals(Boolean.TRUE, ds.getPerUserLifo("key"));
    }

    @Test
    public void testPerUserLifoMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.FALSE);
        ds.setPerUserLifo(values);
        assertEquals(ds.getDefaultLifo(), ds.getPerUserLifo("missingkey"));
    }

    @Test
    public void testPerUserLifoWithUserMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserLifo(user, Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserLifo(user));
        ds.setPerUserLifo("anotheruser", Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserLifo(user));
        assertEquals(Boolean.FALSE, ds.getPerUserLifo("anotheruser"));
    }

    @Test
    public void testPerUserLifoWithUserMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserLifo(user, Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserLifo(user));
    }

    @Test
    public void testPerUserLifoWithUserMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserLifo("whatismyuseragain?", Boolean.FALSE);
        assertEquals(ds.getDefaultLifo(), ds.getPerUserLifo("missingkey"));
    }

    @Test
    public void testPerUserMaxIdleMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        Map<String, Integer> values = new HashMap<>();
        values.put("key", 0);
        ds.setPerUserMaxIdle(values);
        assertEquals((Integer) 0, (Integer) ds.getPerUserMaxIdle("key"));
        values = new HashMap<>();
        values.put("anonymous", 0);
        ds.setPerUserMaxIdle(values);
        assertEquals((Integer) ds.getDefaultMaxIdle(), (Integer) ds.getPerUserMaxIdle("key"));
        assertEquals((Integer) 0, (Integer) ds.getPerUserMaxIdle("anonymous"));
    }

    @Test
    public void testPerUserMaxIdleMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Integer> values = new HashMap<>();
        values.put("key", 1);
        ds.setPerUserMaxIdle(values);
        assertEquals((Integer) 1, (Integer) ds.getPerUserMaxIdle("key"));
    }

    @Test
    public void testPerUserMaxIdleMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Integer> values = new HashMap<>();
        values.put("key", 0);
        ds.setPerUserMaxIdle(values);
        assertEquals((Integer) ds.getDefaultMaxIdle(), (Integer) ds.getPerUserMaxIdle("missingkey"));
    }

    @Test
    public void testPerUserMaxIdleWithUserMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserMaxIdle(user, 0);
        assertEquals((Integer) 0, (Integer) ds.getPerUserMaxIdle(user));
        ds.setPerUserMaxIdle("anotheruser", 0);
        assertEquals((Integer) 0, (Integer) ds.getPerUserMaxIdle(user));
        assertEquals((Integer) 0, (Integer) ds.getPerUserMaxIdle("anotheruser"));
    }

    @Test
    public void testPerUserMaxIdleWithUserMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserMaxIdle(user, 0);
        assertEquals((Integer) 0, (Integer) ds.getPerUserMaxIdle(user));
    }

    @Test
    public void testPerUserMaxIdleWithUserMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserMaxIdle("whatismyuseragain?", 0);
        assertEquals((Integer) ds.getDefaultMaxIdle(), (Integer) ds.getPerUserMaxIdle("missingkey"));
    }

    @Test
    public void testPerUserMaxTotalMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        Map<String, Integer> values = new HashMap<>();
        values.put("key", 0);
        ds.setPerUserMaxTotal(values);
        assertEquals((Integer) 0, (Integer) ds.getPerUserMaxTotal("key"));
        values = new HashMap<>();
        values.put("anonymous", 0);
        ds.setPerUserMaxTotal(values);
        assertEquals((Integer) ds.getDefaultMaxTotal(), (Integer) ds.getPerUserMaxTotal("key"));
        assertEquals((Integer) 0, (Integer) ds.getPerUserMaxTotal("anonymous"));
    }

    @Test
    public void testPerUserMaxTotalMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Integer> values = new HashMap<>();
        values.put("key", 1);
        ds.setPerUserMaxTotal(values);
        assertEquals((Integer) 1, (Integer) ds.getPerUserMaxTotal("key"));
    }

    @Test
    public void testPerUserMaxTotalMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Integer> values = new HashMap<>();
        values.put("key", 0);
        ds.setPerUserMaxTotal(values);
        assertEquals((Integer) ds.getDefaultMaxTotal(), (Integer) ds.getPerUserMaxTotal("missingkey"));
    }

    @Test
    public void testPerUserMaxTotalWithUserMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserMaxTotal(user, 0);
        assertEquals((Integer) 0, (Integer) ds.getPerUserMaxTotal(user));
        ds.setPerUserMaxTotal("anotheruser", 0);
        assertEquals((Integer) 0, (Integer) ds.getPerUserMaxTotal(user));
        assertEquals((Integer) 0, (Integer) ds.getPerUserMaxTotal("anotheruser"));
    }

    @Test
    public void testPerUserMaxTotalWithUserMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserMaxTotal(user, 0);
        assertEquals((Integer) 0, (Integer) ds.getPerUserMaxTotal(user));
    }

    @Test
    public void testPerUserMaxTotalWithUserMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserMaxTotal("whatismyuseragain?", 0);
        assertEquals((Integer) ds.getDefaultMaxTotal(), (Integer) ds.getPerUserMaxTotal("missingkey"));
    }

    @Test
    public void testPerUserMaxWaitMillisMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        Map<String, Long> values = new HashMap<>();
        values.put("key", 0L);
        ds.setPerUserMaxWaitMillis(values);
        assertEquals(0L, ds.getPerUserMaxWaitMillis("key"));
        values = new HashMap<>();
        values.put("anonymous", 0L);
        ds.setPerUserMaxWaitMillis(values);
        assertEquals(ds.getDefaultMaxWaitMillis(), ds.getPerUserMaxWaitMillis("key"));
        assertEquals(0L, ds.getPerUserMaxWaitMillis("anonymous"));
    }

    @Test
    public void testPerUserMaxWaitMillisMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Long> values = new HashMap<>();
        values.put("key", 1L);
        ds.setPerUserMaxWaitMillis(values);
        assertEquals(1L, ds.getPerUserMaxWaitMillis("key"));
    }

    @Test
    public void testPerUserMaxWaitMillisMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Long> values = new HashMap<>();
        values.put("key", 0L);
        ds.setPerUserMaxWaitMillis(values);
        assertEquals(ds.getDefaultMaxWaitMillis(), ds.getPerUserMaxWaitMillis("missingkey"));
    }

    @Test
    public void testPerUserMaxWaitMillisWithUserMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserMaxWaitMillis(user, 0L);
        assertEquals(0L, ds.getPerUserMaxWaitMillis(user));
        ds.setPerUserMaxWaitMillis("anotheruser", 0L);
        assertEquals(0L, ds.getPerUserMaxWaitMillis(user));
        assertEquals(0L, ds.getPerUserMaxWaitMillis("anotheruser"));
    }

    @Test
    public void testPerUserMaxWaitMillisWithUserMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserMaxWaitMillis(user, 0L);
        assertEquals(0L, ds.getPerUserMaxWaitMillis(user));
    }

    @Test
    public void testPerUserMaxWaitMillisWithUserMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserMaxWaitMillis("whatismyuseragain?", 0L);
        assertEquals(ds.getDefaultMaxWaitMillis(), ds.getPerUserMaxWaitMillis("missingkey"));
    }

    @Test
    public void testPerUserMethods() throws Exception {
        final PerUserPoolDataSource tds = (PerUserPoolDataSource) ds;
        tds.setPerUserMaxTotal("u1", 5);
        tds.setPerUserMaxTotal("u2", 5);
        assertEquals(0, tds.getNumActive());
        assertEquals(0, tds.getNumActive("u1"));
        assertEquals(0, tds.getNumActive("u2"));
        assertEquals(0, tds.getNumIdle());
        assertEquals(0, tds.getNumIdle("u1"));
        assertEquals(0, tds.getNumIdle("u2"));
        Connection conn = tds.getConnection();
        assertNotNull(conn);
        assertEquals(1, tds.getNumActive());
        assertEquals(0, tds.getNumActive("u1"));
        assertEquals(0, tds.getNumActive("u2"));
        assertEquals(0, tds.getNumIdle());
        assertEquals(0, tds.getNumIdle("u1"));
        assertEquals(0, tds.getNumIdle("u2"));
        conn.close();
        assertEquals(0, tds.getNumActive());
        assertEquals(0, tds.getNumActive("u1"));
        assertEquals(0, tds.getNumActive("u2"));
        assertEquals(1, tds.getNumIdle());
        assertEquals(0, tds.getNumIdle("u1"));
        assertEquals(0, tds.getNumIdle("u2"));
        conn = tds.getConnection("u1", "p1");
        assertNotNull(conn);
        assertEquals(0, tds.getNumActive());
        assertEquals(1, tds.getNumActive("u1"));
        assertEquals(0, tds.getNumActive("u2"));
        assertEquals(1, tds.getNumIdle());
        assertEquals(0, tds.getNumIdle("u1"));
        assertEquals(0, tds.getNumIdle("u2"));
        conn.close();
        assertEquals(0, tds.getNumActive());
        assertEquals(0, tds.getNumActive("u1"));
        assertEquals(0, tds.getNumActive("u2"));
        assertEquals(1, tds.getNumIdle());
        assertEquals(1, tds.getNumIdle("u1"));
        assertEquals(0, tds.getNumIdle("u2"));
    }

    @Test
    public void testPerUserMinEvictableIdleTimeMillisMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        Map<String, Long> values = new HashMap<>();
        values.put("key", 0L);
        ds.setPerUserMinEvictableIdleTimeMillis(values);
        assertEquals(0L, ds.getPerUserMinEvictableIdleTimeMillis("key"));
        values = new HashMap<>();
        values.put("anonymous", 0L);
        ds.setPerUserMinEvictableIdleTimeMillis(values);
        assertEquals(ds.getDefaultMinEvictableIdleTimeMillis(), ds.getPerUserMinEvictableIdleTimeMillis("key"));
        assertEquals(0L, ds.getPerUserMinEvictableIdleTimeMillis("anonymous"));
    }

    @Test
    public void testPerUserMinEvictableIdleTimeMillisMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Long> values = new HashMap<>();
        values.put("key", 1L);
        ds.setPerUserMinEvictableIdleTimeMillis(values);
        assertEquals(1L, ds.getPerUserMinEvictableIdleTimeMillis("key"));
    }

    @Test
    public void testPerUserMinEvictableIdleTimeMillisMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Long> values = new HashMap<>();
        values.put("key", 0L);
        ds.setPerUserMinEvictableIdleTimeMillis(values);
        assertEquals(ds.getDefaultMinEvictableIdleTimeMillis(), ds.getPerUserMinEvictableIdleTimeMillis("missingkey"));
    }

    @Test
    public void testPerUserMinEvictableIdleTimeMillisWithUserMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserMinEvictableIdleTimeMillis(user, 0L);
        assertEquals(0L, ds.getPerUserMinEvictableIdleTimeMillis(user));
        ds.setPerUserMinEvictableIdleTimeMillis("anotheruser", 0L);
        assertEquals(0L, ds.getPerUserMinEvictableIdleTimeMillis(user));
        assertEquals(0L, ds.getPerUserMinEvictableIdleTimeMillis("anotheruser"));
    }

    @Test
    public void testPerUserMinEvictableIdleTimeMillisWithUserMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserMinEvictableIdleTimeMillis(user, 0L);
        assertEquals(0L, ds.getPerUserMinEvictableIdleTimeMillis(user));
    }

    @Test
    public void testPerUserMinEvictableIdleTimeMillisWithUserMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserMinEvictableIdleTimeMillis("whatismyuseragain?", 0L);
        assertEquals(ds.getDefaultMinEvictableIdleTimeMillis(), ds.getPerUserMinEvictableIdleTimeMillis("missingkey"));
    }

    @Test
    public void testPerUserMinIdleMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        Map<String, Integer> values = new HashMap<>();
        values.put("key", 0);
        ds.setPerUserMinIdle(values);
        assertEquals((Integer) 0, (Integer) ds.getPerUserMinIdle("key"));
        values = new HashMap<>();
        values.put("anonymous", 0);
        ds.setPerUserMinIdle(values);
        assertEquals((Integer) ds.getDefaultMinIdle(), (Integer) ds.getPerUserMinIdle("key"));
        assertEquals((Integer) 0, (Integer) ds.getPerUserMinIdle("anonymous"));
    }

    @Test
    public void testPerUserMinIdleMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Integer> values = new HashMap<>();
        values.put("key", 1);
        ds.setPerUserMinIdle(values);
        assertEquals((Integer) 1, (Integer) ds.getPerUserMinIdle("key"));
    }

    @Test
    public void testPerUserMinIdleMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Integer> values = new HashMap<>();
        values.put("key", 0);
        ds.setPerUserMinIdle(values);
        assertEquals((Integer) ds.getDefaultMinIdle(), (Integer) ds.getPerUserMinIdle("missingkey"));
    }

    @Test
    public void testPerUserMinIdleWithUserMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserMinIdle(user, 0);
        assertEquals((Integer) 0, (Integer) ds.getPerUserMinIdle(user));
        ds.setPerUserMinIdle("anotheruser", 0);
        assertEquals((Integer) 0, (Integer) ds.getPerUserMinIdle(user));
        assertEquals((Integer) 0, (Integer) ds.getPerUserMinIdle("anotheruser"));
    }

    @Test
    public void testPerUserMinIdleWithUserMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserMinIdle(user, 0);
        assertEquals((Integer) 0, (Integer) ds.getPerUserMinIdle(user));
    }

    @Test
    public void testPerUserMinIdleWithUserMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserMinIdle("whatismyuseragain?", 0);
        assertEquals((Integer) ds.getDefaultMinIdle(), (Integer) ds.getPerUserMinIdle("missingkey"));
    }

    @Test
    public void testPerUserNumTestsPerEvictionRunMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        Map<String, Integer> values = new HashMap<>();
        values.put("key", 0);
        ds.setPerUserNumTestsPerEvictionRun(values);
        assertEquals((Integer) 0, (Integer) ds.getPerUserNumTestsPerEvictionRun("key"));
        values = new HashMap<>();
        values.put("anonymous", 0);
        ds.setPerUserNumTestsPerEvictionRun(values);
        assertEquals((Integer) ds.getDefaultNumTestsPerEvictionRun(), (Integer) ds.getPerUserNumTestsPerEvictionRun("key"));
        assertEquals((Integer) 0, (Integer) ds.getPerUserNumTestsPerEvictionRun("anonymous"));
    }

    @Test
    public void testPerUserNumTestsPerEvictionRunMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Integer> values = new HashMap<>();
        values.put("key", 1);
        ds.setPerUserNumTestsPerEvictionRun(values);
        assertEquals((Integer) 1, (Integer) ds.getPerUserNumTestsPerEvictionRun("key"));
    }

    @Test
    public void testPerUserNumTestsPerEvictionRunMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Integer> values = new HashMap<>();
        values.put("key", 0);
        ds.setPerUserNumTestsPerEvictionRun(values);
        assertEquals((Integer) ds.getDefaultNumTestsPerEvictionRun(), (Integer) ds.getPerUserNumTestsPerEvictionRun("missingkey"));
    }

    @Test
    public void testPerUserNumTestsPerEvictionRunWithUserMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserNumTestsPerEvictionRun(user, 0);
        assertEquals((Integer) 0, (Integer) ds.getPerUserNumTestsPerEvictionRun(user));
        ds.setPerUserNumTestsPerEvictionRun("anotheruser", 0);
        assertEquals((Integer) 0, (Integer) ds.getPerUserNumTestsPerEvictionRun(user));
        assertEquals((Integer) 0, (Integer) ds.getPerUserNumTestsPerEvictionRun("anotheruser"));
    }

    @Test
    public void testPerUserNumTestsPerEvictionRunWithUserMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserNumTestsPerEvictionRun(user, 0);
        assertEquals((Integer) 0, (Integer) ds.getPerUserNumTestsPerEvictionRun(user));
    }

    @Test
    public void testPerUserNumTestsPerEvictionRunWithUserMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserNumTestsPerEvictionRun("whatismyuseragain?", 0);
        assertEquals((Integer) ds.getDefaultNumTestsPerEvictionRun(), (Integer) ds.getPerUserNumTestsPerEvictionRun("missingkey"));
    }

    @Test
    public void testPerUserSoftMinEvictableIdleTimeMillisMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        Map<String, Long> values = new HashMap<>();
        values.put("key", 0L);
        ds.setPerUserSoftMinEvictableIdleTimeMillis(values);
        assertEquals(0L, ds.getPerUserSoftMinEvictableIdleTimeMillis("key"));
        values = new HashMap<>();
        values.put("anonymous", 0L);
        ds.setPerUserSoftMinEvictableIdleTimeMillis(values);
        assertEquals(ds.getDefaultSoftMinEvictableIdleTimeMillis(), ds.getPerUserSoftMinEvictableIdleTimeMillis("key"));
        assertEquals(0L, ds.getPerUserSoftMinEvictableIdleTimeMillis("anonymous"));
    }

    @Test
    public void testPerUserSoftMinEvictableIdleTimeMillisMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Long> values = new HashMap<>();
        values.put("key", 1L);
        ds.setPerUserSoftMinEvictableIdleTimeMillis(values);
        assertEquals(1L, ds.getPerUserSoftMinEvictableIdleTimeMillis("key"));
    }

    @Test
    public void testPerUserSoftMinEvictableIdleTimeMillisMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Long> values = new HashMap<>();
        values.put("key", 0L);
        ds.setPerUserSoftMinEvictableIdleTimeMillis(values);
        assertEquals(ds.getDefaultSoftMinEvictableIdleTimeMillis(), ds.getPerUserSoftMinEvictableIdleTimeMillis("missingkey"));
    }

    @Test
    public void testPerUserSoftMinEvictableIdleTimeMillisWithUserMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserSoftMinEvictableIdleTimeMillis(user, 0L);
        assertEquals(0L, ds.getPerUserSoftMinEvictableIdleTimeMillis(user));
        ds.setPerUserSoftMinEvictableIdleTimeMillis("anotheruser", 0L);
        assertEquals(0L, ds.getPerUserSoftMinEvictableIdleTimeMillis(user));
        assertEquals(0L, ds.getPerUserSoftMinEvictableIdleTimeMillis("anotheruser"));
    }

    @Test
    public void testPerUserSoftMinEvictableIdleTimeMillisWithUserMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserSoftMinEvictableIdleTimeMillis(user, 0L);
        assertEquals(0L, ds.getPerUserSoftMinEvictableIdleTimeMillis(user));
    }

    @Test
    public void testPerUserSoftMinEvictableIdleTimeMillisWithUserMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserSoftMinEvictableIdleTimeMillis("whatismyuseragain?", 0L);
        assertEquals(ds.getDefaultSoftMinEvictableIdleTimeMillis(), ds.getPerUserSoftMinEvictableIdleTimeMillis("missingkey"));
    }

    @Test
    public void testPerUserTestOnBorrowMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.FALSE);
        ds.setPerUserTestOnBorrow(values);
        assertEquals(Boolean.FALSE, ds.getPerUserTestOnBorrow("key"));
        values = new HashMap<>();
        values.put("anonymous", Boolean.FALSE);
        ds.setPerUserTestOnBorrow(values);
        assertEquals(ds.getDefaultTestOnBorrow(), ds.getPerUserTestOnBorrow("key"));
        assertEquals(Boolean.FALSE, ds.getPerUserTestOnBorrow("anonymous"));
    }

    @Test
    public void testPerUserTestOnBorrowMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.TRUE);
        ds.setPerUserTestOnBorrow(values);
        assertEquals(Boolean.TRUE, ds.getPerUserTestOnBorrow("key"));
    }

    @Test
    public void testPerUserTestOnBorrowMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.FALSE);
        ds.setPerUserTestOnBorrow(values);
        assertEquals(ds.getDefaultTestOnBorrow(), ds.getPerUserTestOnBorrow("missingkey"));
    }

    @Test
    public void testPerUserTestOnBorrowWithUserMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserTestOnBorrow(user, Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserTestOnBorrow(user));
        ds.setPerUserTestOnBorrow("anotheruser", Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserTestOnBorrow(user));
        assertEquals(Boolean.FALSE, ds.getPerUserTestOnBorrow("anotheruser"));
    }

    @Test
    public void testPerUserTestOnBorrowWithUserMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserTestOnBorrow(user, Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserTestOnBorrow(user));
    }

    @Test
    public void testPerUserTestOnBorrowWithUserMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserTestOnBorrow("whatismyuseragain?", Boolean.FALSE);
        assertEquals(ds.getDefaultTestOnBorrow(), ds.getPerUserTestOnBorrow("missingkey"));
    }

    @Test
    public void testPerUserTestOnCreateMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.FALSE);
        ds.setPerUserTestOnCreate(values);
        assertEquals(Boolean.FALSE, ds.getPerUserTestOnCreate("key"));
        values = new HashMap<>();
        values.put("anonymous", Boolean.FALSE);
        ds.setPerUserTestOnCreate(values);
        assertEquals(ds.getDefaultTestOnCreate(), ds.getPerUserTestOnCreate("key"));
        assertEquals(Boolean.FALSE, ds.getPerUserTestOnCreate("anonymous"));
    }

    @Test
    public void testPerUserTestOnCreateMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.TRUE);
        ds.setPerUserTestOnCreate(values);
        assertEquals(Boolean.TRUE, ds.getPerUserTestOnCreate("key"));
    }

    @Test
    public void testPerUserTestOnCreateMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.FALSE);
        ds.setPerUserTestOnCreate(values);
        assertEquals(ds.getDefaultTestOnCreate(), ds.getPerUserTestOnCreate("missingkey"));
    }

    @Test
    public void testPerUserTestOnCreateWithUserMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserTestOnCreate(user, Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserTestOnCreate(user));
        ds.setPerUserTestOnCreate("anotheruser", Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserTestOnCreate(user));
        assertEquals(Boolean.FALSE, ds.getPerUserTestOnCreate("anotheruser"));
    }

    @Test
    public void testPerUserTestOnCreateWithUserMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserTestOnCreate(user, Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserTestOnCreate(user));
    }

    @Test
    public void testPerUserTestOnCreateWithUserMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserTestOnCreate("whatismyuseragain?", Boolean.FALSE);
        assertEquals(ds.getDefaultTestOnCreate(), ds.getPerUserTestOnCreate("missingkey"));
    }

    @Test
    public void testPerUserTestOnReturnMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.FALSE);
        ds.setPerUserTestOnReturn(values);
        assertEquals(Boolean.FALSE, ds.getPerUserTestOnReturn("key"));
        values = new HashMap<>();
        values.put("anonymous", Boolean.FALSE);
        ds.setPerUserTestOnReturn(values);
        assertEquals(ds.getDefaultTestOnReturn(), ds.getPerUserTestOnReturn("key"));
        assertEquals(Boolean.FALSE, ds.getPerUserTestOnReturn("anonymous"));
    }

    @Test
    public void testPerUserTestOnReturnMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.TRUE);
        ds.setPerUserTestOnReturn(values);
        assertEquals(Boolean.TRUE, ds.getPerUserTestOnReturn("key"));
    }

    @Test
    public void testPerUserTestOnReturnMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.FALSE);
        ds.setPerUserTestOnReturn(values);
        assertEquals(ds.getDefaultTestOnReturn(), ds.getPerUserTestOnReturn("missingkey"));
    }

    @Test
    public void testPerUserTestOnReturnWithUserMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserTestOnReturn(user, Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserTestOnReturn(user));
        ds.setPerUserTestOnReturn("anotheruser", Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserTestOnReturn(user));
        assertEquals(Boolean.FALSE, ds.getPerUserTestOnReturn("anotheruser"));
    }

    @Test
    public void testPerUserTestOnReturnWithUserMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserTestOnReturn(user, Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserTestOnReturn(user));
    }

    @Test
    public void testPerUserTestOnReturnWithUserMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserTestOnReturn("whatismyuseragain?", Boolean.FALSE);
        assertEquals(ds.getDefaultTestOnReturn(), ds.getPerUserTestOnReturn("missingkey"));
    }

    @Test
    public void testPerUserTestWhileIdleMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.FALSE);
        ds.setPerUserTestWhileIdle(values);
        assertEquals(Boolean.FALSE, ds.getPerUserTestWhileIdle("key"));
        values = new HashMap<>();
        values.put("anonymous", Boolean.FALSE);
        ds.setPerUserTestWhileIdle(values);
        assertEquals(ds.getDefaultTestWhileIdle(), ds.getPerUserTestWhileIdle("key"));
        assertEquals(Boolean.FALSE, ds.getPerUserTestWhileIdle("anonymous"));
    }

    @Test
    public void testPerUserTestWhileIdleMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.TRUE);
        ds.setPerUserTestWhileIdle(values);
        assertEquals(Boolean.TRUE, ds.getPerUserTestWhileIdle("key"));
    }

    @Test
    public void testPerUserTestWhileIdleMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Boolean> values = new HashMap<>();
        values.put("key", Boolean.FALSE);
        ds.setPerUserTestWhileIdle(values);
        assertEquals(ds.getDefaultTestWhileIdle(), ds.getPerUserTestWhileIdle("missingkey"));
    }

    @Test
    public void testPerUserTestWhileIdleWithUserMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserTestWhileIdle(user, Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserTestWhileIdle(user));
        ds.setPerUserTestWhileIdle("anotheruser", Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserTestWhileIdle(user));
        assertEquals(Boolean.FALSE, ds.getPerUserTestWhileIdle("anotheruser"));
    }

    @Test
    public void testPerUserTestWhileIdleWithUserMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserTestWhileIdle(user, Boolean.FALSE);
        assertEquals(Boolean.FALSE, ds.getPerUserTestWhileIdle(user));
    }

    @Test
    public void testPerUserTestWhileIdleWithUserMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserTestWhileIdle("whatismyuseragain?", Boolean.FALSE);
        assertEquals(ds.getDefaultTestWhileIdle(), ds.getPerUserTestWhileIdle("missingkey"));
    }

    @Test
    public void testPerUserTimeBetweenEvictionRunsMillisMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        Map<String, Long> values = new HashMap<>();
        values.put("key", 0L);
        ds.setPerUserTimeBetweenEvictionRunsMillis(values);
        assertEquals(0L, ds.getPerUserTimeBetweenEvictionRunsMillis("key"));
        values = new HashMap<>();
        values.put("anonymous", 0L);
        ds.setPerUserTimeBetweenEvictionRunsMillis(values);
        assertEquals(ds.getDefaultTimeBetweenEvictionRunsMillis(), ds.getPerUserTimeBetweenEvictionRunsMillis("key"));
        assertEquals(0L, ds.getPerUserTimeBetweenEvictionRunsMillis("anonymous"));
    }

    @Test
    public void testPerUserTimeBetweenEvictionRunsMillisMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Long> values = new HashMap<>();
        values.put("key", 1L);
        ds.setPerUserTimeBetweenEvictionRunsMillis(values);
        assertEquals(1L, ds.getPerUserTimeBetweenEvictionRunsMillis("key"));
    }

    @Test
    public void testPerUserTimeBetweenEvictionRunsMillisMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        final Map<String, Long> values = new HashMap<>();
        values.put("key", 0L);
        ds.setPerUserTimeBetweenEvictionRunsMillis(values);
        assertEquals(ds.getDefaultTimeBetweenEvictionRunsMillis(), ds.getPerUserTimeBetweenEvictionRunsMillis("missingkey"));
    }

    @Test
    public void testPerUserTimeBetweenEvictionRunsMillisWithUserMapInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserTimeBetweenEvictionRunsMillis(user, 0L);
        assertEquals(0L, ds.getPerUserTimeBetweenEvictionRunsMillis(user));
        ds.setPerUserTimeBetweenEvictionRunsMillis("anotheruser", 0L);
        assertEquals(0L, ds.getPerUserTimeBetweenEvictionRunsMillis(user));
        assertEquals(0L, ds.getPerUserTimeBetweenEvictionRunsMillis("anotheruser"));
    }

    @Test
    public void testPerUserTimeBetweenEvictionRunsMillisWithUserMapNotInitialized() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserTimeBetweenEvictionRunsMillis(user, 0L);
        assertEquals(0L, ds.getPerUserTimeBetweenEvictionRunsMillis(user));
    }

    @Test
    public void testPerUserTimeBetweenEvictionRunsMillisWithUserMapNotInitializedMissingKey() {
        final PerUserPoolDataSource ds = (PerUserPoolDataSource) this.ds;
        ds.setPerUserTimeBetweenEvictionRunsMillis("whatismyuseragain?", 0L);
        assertEquals(ds.getDefaultTimeBetweenEvictionRunsMillis(), ds.getPerUserTimeBetweenEvictionRunsMillis("missingkey"));
    }

    @Test
    public void testSerialization() throws Exception {
        final Connection conn = ds.getConnection();
        conn.close();
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ObjectOutputStream(baos);
        out.writeObject(ds);
        out.close();
        final byte[] b = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(b);
        final ObjectInputStream in = new ObjectInputStream(bais);
        final Object obj = in.readObject();
        in.close();
        assertEquals(1, ((PerUserPoolDataSource) obj).getNumIdle());
    }

    @Override
    @Test
    public void testSimple() throws Exception {
        final Connection conn = ds.getConnection();
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

    @Override
    @Test
    public void testSimple2()
            throws Exception {
        Connection conn = ds.getConnection();
        assertNotNull(conn);
        PreparedStatement stmt = conn.prepareStatement("select * from dual");
        assertNotNull(stmt);
        ResultSet rset = stmt.executeQuery();
        assertNotNull(rset);
        assertTrue(rset.next());
        rset.close();
        stmt.close();
        stmt = conn.prepareStatement("select * from dual");
        assertNotNull(stmt);
        rset = stmt.executeQuery();
        assertNotNull(rset);
        assertTrue(rset.next());
        rset.close();
        stmt.close();
        conn.close();
        try (Statement ignored = conn.createStatement()) {
            fail("Can't use closed connections");
        } catch (final SQLException e) {
        }

        conn = ds.getConnection();
        assertNotNull(conn);
        stmt = conn.prepareStatement("select * from dual");
        assertNotNull(stmt);
        rset = stmt.executeQuery();
        assertNotNull(rset);
        assertTrue(rset.next());
        rset.close();
        stmt.close();
        stmt = conn.prepareStatement("select * from dual");
        assertNotNull(stmt);
        rset = stmt.executeQuery();
        assertNotNull(rset);
        assertTrue(rset.next());
        rset.close();
        stmt.close();
        conn.close();
        conn = null;
    }

    @Test
    public void testSimpleWithUsername() throws Exception {
        final Connection conn = ds.getConnection("u1", "p1");
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
    public void testTransactionIsolationBehavior() throws Exception {
        final Connection conn = getConnection();
        assertNotNull(conn);
        assertEquals(Connection.TRANSACTION_READ_COMMITTED,
                conn.getTransactionIsolation());
        conn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
        conn.close();
        final Connection conn2 = getConnection();
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, conn2.getTransactionIsolation());
        final Connection conn3 = getConnection();
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, conn3.getTransactionIsolation());
        conn2.close();
        conn3.close();
    }

    @Test
    public void testUnregisteredUser() throws Exception {
        final PerUserPoolDataSource tds = (PerUserPoolDataSource) ds;
        assertEquals(0, tds.getNumActive());
        assertEquals(0, tds.getNumIdle());
        Connection conn = tds.getConnection();
        assertNotNull(conn);
        assertEquals(1, tds.getNumActive());
        assertEquals(0, tds.getNumIdle());
        conn.close();
        assertEquals(0, tds.getNumActive());
        assertEquals(1, tds.getNumIdle());
        conn = tds.getConnection("u1", "p1");
        assertNotNull(conn);
        assertEquals(0, tds.getNumActive());
        assertEquals(1, tds.getNumIdle());
        assertEquals(1, tds.getNumActive("u1"));
        assertEquals(0, tds.getNumIdle("u1"));
        conn.close();
        assertEquals(0, tds.getNumActive());
        assertEquals(1, tds.getNumIdle());
        assertEquals(0, tds.getNumActive("u1"));
        assertEquals(1, tds.getNumIdle("u1"));
    }
}
