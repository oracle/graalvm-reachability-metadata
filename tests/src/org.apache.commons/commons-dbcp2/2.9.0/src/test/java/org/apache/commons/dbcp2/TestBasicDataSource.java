/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org_apache_commons.commons_dbcp2.StackMessageLog;
import org_apache_commons.commons_dbcp2.TesterClassLoader;
import org.apache.commons.logging.LogFactory;
import org.hamcrest.CoreMatchers;

import javax.management.AttributeNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.sql.DataSource;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings({"resource"})
public class TestBasicDataSource extends TestConnectionPool {
    private static final String CATALOG = "test catalog";

    @BeforeAll
    public static void setUpClass() {
        LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", "org_apache_commons.commons_dbcp2.StackMessageLog");
    }

    protected BasicDataSource ds;

    protected BasicDataSource createDataSource() throws Exception {
        return new BasicDataSource();
    }

    @Override
    protected Connection getConnection() throws Exception {
        return ds.getConnection();
    }

    @BeforeEach
    public void setUp() throws Exception {
        ds = createDataSource();
        ds.setDriverClassName("org.apache.commons.dbcp2.TesterDriver");
        ds.setUrl("jdbc:apache:commons:testdriver");
        ds.setMaxTotal(getMaxTotal());
        ds.setMaxWaitMillis(getMaxWaitMillis());
        ds.setDefaultAutoCommit(Boolean.TRUE);
        ds.setDefaultReadOnly(Boolean.FALSE);
        ds.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        ds.setDefaultCatalog(CATALOG);
        ds.setUsername("userName");
        ds.setPassword("password");
        ds.setValidationQuery("SELECT DUMMY FROM DUAL");
        ds.setConnectionInitSqls(Arrays.asList("SELECT 1", "SELECT 2"));
        ds.setDriverClassLoader(new TesterClassLoader());
        ds.setJmxName("org.apache.commons.dbcp2:name=test");
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        ds.close();
        ds = null;
    }

    @Test
    public void testAccessToUnderlyingConnectionAllowed() throws Exception {
        ds.setAccessToUnderlyingConnectionAllowed(true);
        assertTrue(ds.isAccessToUnderlyingConnectionAllowed());
        try (Connection conn = getConnection()) {
            Connection dconn = ((DelegatingConnection<?>) conn).getDelegate();
            assertNotNull(dconn);
            dconn = ((DelegatingConnection<?>) conn).getInnermostDelegate();
            assertNotNull(dconn);
            assertTrue(dconn instanceof TesterConnection);
        }
    }

    @Test
    public void testClose() throws Exception {
        ds.setAccessToUnderlyingConnectionAllowed(true);
        final Connection activeConnection = getConnection();
        final Connection rawActiveConnection = ((DelegatingConnection<?>) activeConnection).getInnermostDelegate();
        assertFalse(activeConnection.isClosed());
        assertFalse(rawActiveConnection.isClosed());
        final Connection idleConnection = getConnection();
        final Connection rawIdleConnection = ((DelegatingConnection<?>) idleConnection).getInnermostDelegate();
        assertFalse(idleConnection.isClosed());
        assertFalse(rawIdleConnection.isClosed());
        idleConnection.close();
        assertTrue(idleConnection.isClosed());
        assertFalse(rawIdleConnection.isClosed());
        ds.close();
        assertTrue(rawIdleConnection.isClosed());
        assertFalse(activeConnection.isClosed());
        assertFalse(rawActiveConnection.isClosed());
        activeConnection.close();
        assertTrue(activeConnection.isClosed());
        assertTrue(rawActiveConnection.isClosed());
        try {
            getConnection();
            fail("Expecting SQLException");
        } catch (final SQLException ignored) {
        }
        ds.close();

    }

    @Test
    public void testConcurrentInitBorrow() throws Exception {
        ds.setDriverClassName("org.apache.commons.dbcp2.TesterConnectionDelayDriver");
        ds.setUrl("jdbc:apache:commons:testerConnectionDelayDriver:50");
        ds.setInitialSize(8);
        final TestThread testThread = new TestThread(1, 0);
        final Thread t = new Thread(testThread);
        t.start();
        Thread.sleep(100);
        ds.getConnection();
        assertTrue(ds.getConnectionPool().getNumIdle() > 5);
        t.join();
        assertFalse(testThread.failed());
        ds.close();
    }

    @Test
    public void testConcurrentInvalidateBorrow() throws Exception {
        ds.setDriverClassName("org.apache.commons.dbcp2.TesterConnRequestCountDriver");
        ds.setUrl("jdbc:apache:commons:testerConnRequestCountDriver");
        ds.setTestOnBorrow(true);
        ds.setValidationQuery("SELECT DUMMY FROM DUAL");
        ds.setMaxTotal(8);
        ds.setLifo(true);
        ds.setMaxWaitMillis(-1);
        final TestThread testThread1 = new TestThread(1000, 0);
        final Thread t1 = new Thread(testThread1);
        t1.start();
        final TestThread testThread2 = new TestThread(1000, 0);
        final Thread t2 = new Thread(testThread1);
        t2.start();
        for (int i = 0; i < 1000; i++) {
            final Connection conn = ds.getConnection();
            ds.invalidateConnection(conn);
        }
        t1.join();
        t2.join();
        assertFalse(testThread1.failed());
        assertFalse(testThread2.failed());
        ds.close();
    }

    @Test
    public void testCreateConnectionFactory() throws Exception {
        Properties properties = new Properties();
        properties.put("initialSize", "1");
        properties.put("driverClassName", "org.apache.commons.dbcp2.TesterDriver");
        properties.put("url", "jdbc:apache:commons:testdriver");
        properties.put("username", "foo");
        properties.put("password", "bar");
        BasicDataSource ds = BasicDataSourceFactory.createDataSource(properties);
        Connection conn = ds.getConnection();
        assertNotNull(conn);
        conn.close();
        ds.close();
        properties = new Properties();
        properties.put("initialSize", "1");
        properties.put("driverClassName", "org.apache.commons.dbcp2.TesterDriver");
        properties.put("url", "jdbc:apache:commons:testdriver");
        properties.put("username", "foo");
        properties.put("password", "bar");
        properties.put("connectionFactoryClassName", "org.apache.commons.dbcp2.TesterConnectionFactory");
        ds = BasicDataSourceFactory.createDataSource(properties);
        conn = ds.getConnection();
        assertNotNull(conn);
        conn.close();
        ds.close();
    }

    @Test
    public void testCreateDataSourceCleanupEvictor() throws Exception {
        ds.close();
        ds = null;
        ds = createDataSource();
        ds.setDriverClassName("org.apache.commons.dbcp2.TesterConnRequestCountDriver");
        ds.setUrl("jdbc:apache:commons:testerConnRequestCountDriver");
        ds.setValidationQuery("SELECT DUMMY FROM DUAL");
        ds.setUsername("userName");
        ds.setPassword("wrong");
        ds.setTimeBetweenEvictionRunsMillis(100);
        ds.setMinIdle(2);
        synchronized (TesterConnRequestCountDriver.class) {
            TesterConnRequestCountDriver.initConnRequestCount();
            for (int i = 0; i < 10; i++) {
                try {
                    @SuppressWarnings("unused") final DataSource ds2 = ds.createDataSource();
                } catch (final SQLException ignored) {
                }
            }
            Thread.sleep(1000);
            assertEquals(10, TesterConnRequestCountDriver.getConnectionRequestCount());
        }
        assertNull(ds.getConnectionPool());
    }

    @Test
    public void testCreateDataSourceCleanupThreads() throws Exception {
        ds.close();
        ds = null;
        ds = createDataSource();
        ds.setDriverClassName("org.apache.commons.dbcp2.TesterDriver");
        ds.setUrl("jdbc:apache:commons:testdriver");
        ds.setMaxTotal(getMaxTotal());
        ds.setMaxWaitMillis(getMaxWaitMillis());
        ds.setDefaultAutoCommit(Boolean.TRUE);
        ds.setDefaultReadOnly(Boolean.FALSE);
        ds.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        ds.setDefaultCatalog(CATALOG);
        ds.setUsername("userName");
        ds.setTimeBetweenEvictionRunsMillis(100);
        ds.setPassword("wrong");
        ds.setValidationQuery("SELECT DUMMY FROM DUAL");
        final int threadCount = Thread.activeCount();
        IntStream.range(0, 10).forEach(i -> {
            try (Connection ignored = ds.getConnection()) {
                assertNotNull(ignored);
            } catch (final SQLException ignored) {
            }
        });
        assertTrue(Thread.activeCount() <= threadCount + 1);
    }

    @Test
    public void testDefaultCatalog() throws Exception {
        final Connection[] c = new Connection[getMaxTotal()];
        for (int i = 0; i < c.length; i++) {
            c[i] = getConnection();
            assertNotNull(c[i]);
            assertEquals(CATALOG, c[i].getCatalog());
        }
        for (final Connection element : c) {
            element.setCatalog("error");
            element.close();
        }
        for (int i = 0; i < c.length; i++) {
            c[i] = getConnection();
            assertNotNull(c[i]);
            assertEquals(CATALOG, c[i].getCatalog());
        }
        for (final Connection element : c) {
            element.close();
        }
    }

    @Test
    public void testDisconnectSqlCodes() throws Exception {
        final ArrayList<String> disconnectionSqlCodes = new ArrayList<>();
        disconnectionSqlCodes.add("XXX");
        ds.setDisconnectionSqlCodes(disconnectionSqlCodes);
        ds.setFastFailValidation(true);
        ds.getConnection();
        final PoolableConnectionFactory pcf =
                (PoolableConnectionFactory) ds.getConnectionPool().getFactory();
        assertTrue(pcf.isFastFailValidation());
        assertTrue(pcf.getDisconnectionSqlCodes().contains("XXX"));
        assertEquals(1, pcf.getDisconnectionSqlCodes().size());
    }

    @Test
    @Disabled("https://github.com/oracle/graal/issues/5913")
    public void testDriverClassLoader() throws Exception {
        getConnection();
        final ClassLoader cl = ds.getDriverClassLoader();
        assertNotNull(cl);
        assertTrue(cl instanceof TesterClassLoader);
        assertTrue(((TesterClassLoader) cl).didLoad(ds.getDriverClassName()));
    }

    @Test
    public void testEmptyInitConnectionSql() {
        ds.setConnectionInitSqls(Arrays.asList("", "   "));
        assertNotNull(ds.getConnectionInitSqls());
        assertEquals(0, ds.getConnectionInitSqls().size());
        ds.setConnectionInitSqls(null);
        assertNotNull(ds.getConnectionInitSqls());
        assertEquals(0, ds.getConnectionInitSqls().size());
    }

    @Test
    public void testEmptyValidationQuery() {
        assertNotNull(ds.getValidationQuery());
        ds.setValidationQuery("");
        assertNull(ds.getValidationQuery());
        ds.setValidationQuery("   ");
        assertNull(ds.getValidationQuery());
    }

    @Test
    public void testInitialSize() throws Exception {
        ds.setMaxTotal(20);
        ds.setMaxIdle(20);
        ds.setInitialSize(10);

        final Connection conn = getConnection();
        assertNotNull(conn);
        conn.close();

        assertEquals(0, ds.getNumActive());
        assertEquals(10, ds.getNumIdle());
    }

    @Test
    public void testInstanceNotFoundExceptionLogSuppressed() throws Exception {
        final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try (Connection ignored = ds.getConnection()) {
            assertNotNull(ignored);
        }
        final ObjectName objectName = new ObjectName(ds.getJmxName());
        if (mbs.isRegistered(objectName)) {
            mbs.unregisterMBean(objectName);
        }
        StackMessageLog.clear();
        ds.close();
        assertThat(StackMessageLog.popMessage(),
                CoreMatchers.not(CoreMatchers.containsString("InstanceNotFoundException")));
        assertNull(ds.getRegisteredJmxName());
    }

    @Test
    public void testInvalidateConnection() throws Exception {
        ds.setMaxTotal(2);
        try (Connection conn1 = ds.getConnection()) {
            try (Connection conn2 = ds.getConnection()) {
                ds.invalidateConnection(conn1);
                assertTrue(conn1.isClosed());
                assertEquals(1, ds.getNumActive());
                assertEquals(0, ds.getNumIdle());
                try (Connection ignored = ds.getConnection()) {
                    conn2.close();
                }
            }
        }
    }

    @Test
    public void testInvalidConnectionInitSql() {
        try {
            ds.setConnectionInitSqls(Arrays.asList("SELECT 1", "invalid"));
            try (Connection ignored = ds.getConnection()) {
                assertNotNull(ignored);
            }
            fail("expected SQLException");
        } catch (final SQLException e) {
            if (!e.toString().contains("invalid")) {
                fail("expected detailed error message");
            }
        }
    }

    @Test
    public void testInvalidValidationQuery() {
        ds.setValidationQuery("invalid");
        try (Connection ignored = ds.getConnection()) {
            fail("expected SQLException");
        } catch (final SQLException e) {
            if (!e.toString().contains("invalid")) {
                fail("expected detailed error message");
            }
        }
    }

    @Test
    public void testIsClosedFailure() throws SQLException {
        ds.setAccessToUnderlyingConnectionAllowed(true);
        final Connection conn = ds.getConnection();
        assertNotNull(conn);
        assertEquals(1, ds.getNumActive());
        final TesterConnection tconn = (TesterConnection) ((DelegatingConnection<?>) conn).getInnermostDelegate();
        tconn.setFailure(new IOException("network error"));
        try {
            conn.close();
            fail("Expected SQLException");
        } catch (final SQLException ignored) {
        }
        assertEquals(0, ds.getNumActive());
    }

    @Test
    public void testIsWrapperFor() throws Exception {
        assertTrue(ds.isWrapperFor(BasicDataSource.class));
        assertTrue(ds.isWrapperFor(AutoCloseable.class));
        assertFalse(ds.isWrapperFor(String.class));
        assertFalse(ds.isWrapperFor(null));
    }

    @Test
    public void testJmxDisabled() throws Exception {
        final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        final ObjectName commons = new ObjectName("org.apache.commons.*:*");
        final Set<ObjectName> results = mbs.queryNames(commons, null);
        for (final ObjectName result : results) {
            mbs.unregisterMBean(result);
        }
        ds.setJmxName(null);
        ds.setPoolPreparedStatements(true);
        ds.getConnection();
        assertEquals(0, mbs.queryNames(commons, null).size());
    }

    @Test
    public void testJmxDoesNotExposePassword() throws Exception {
        final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try (Connection ignored = ds.getConnection()) {
            assertNotNull(ignored);
        }
        final ObjectName objectName = new ObjectName(ds.getJmxName());
        final MBeanAttributeInfo[] attributes = mbs.getMBeanInfo(objectName).getAttributes();
        assertTrue(attributes != null && attributes.length > 0);
        Arrays.asList(attributes).forEach(attrInfo -> assertFalse("password".equalsIgnoreCase(attrInfo.getName())));
        assertThrows(AttributeNotFoundException.class, () -> mbs.getAttribute(objectName, "Password"));
    }

    @Test
    public void testManualConnectionEvict() throws Exception {
        ds.setMinIdle(0);
        ds.setMaxIdle(4);
        ds.setMinEvictableIdleTimeMillis(10);
        ds.setNumTestsPerEvictionRun(2);
        final Connection ds2 = ds.createDataSource().getConnection();
        final Connection ds3 = ds.createDataSource().getConnection();
        assertEquals(0, ds.getNumIdle());
        ds2.close();
        ds3.close();
        Thread.sleep(100);
        assertEquals(2, ds.getNumIdle());
        ds.evict();
        assertEquals(0, ds.getNumIdle());
    }

    @Test
    public void testMaxConnLifetimeExceeded() throws Exception {
        try {
            StackMessageLog.lock();
            ds.setMaxConnLifetimeMillis(100);
            final Connection conn = ds.getConnection();
            assertEquals(1, ds.getNumActive());
            Thread.sleep(500);
            conn.close();
            assertEquals(0, ds.getNumIdle());
            final String message = StackMessageLog.popMessage();
            Assertions.assertNotNull(message);
            assertTrue(message.indexOf("exceeds the maximum permitted value") > 0);
        } finally {
            StackMessageLog.clear();
            StackMessageLog.unLock();
        }
    }

    @Test
    public void testMaxConnLifetimeExceededMutedLog() throws Exception {
        try {
            StackMessageLog.lock();
            StackMessageLog.clear();
            ds.setMaxConnLifetimeMillis(100);
            ds.setLogExpiredConnections(false);
            try (Connection ignored = ds.getConnection()) {
                assertEquals(1, ds.getNumActive());
                Thread.sleep(500);
            }
            assertEquals(0, ds.getNumIdle());
            assertTrue(StackMessageLog.isEmpty(), StackMessageLog.getAll().toString());
        } finally {
            StackMessageLog.clear();
            StackMessageLog.unLock();
        }
    }

    @Test
    public void testMaxTotalZero() {
        ds.setMaxTotal(0);
        try {
            final Connection conn = ds.getConnection();
            assertNotNull(conn);
            fail("SQLException expected");
        } catch (final SQLException ignored) {
        }
    }

    @Test
    public void testMutateAbandonedConfig() throws Exception {
        final Properties properties = new Properties();
        properties.put("initialSize", "1");
        properties.put("driverClassName", "org.apache.commons.dbcp2.TesterDriver");
        properties.put("url", "jdbc:apache:commons:testdriver");
        properties.put("username", "foo");
        properties.put("password", "bar");
        final BasicDataSource ds = BasicDataSourceFactory.createDataSource(properties);
        final boolean original = ds.getConnectionPool().getLogAbandoned();
        ds.setLogAbandoned(!original);
        Assertions.assertNotEquals(original, ds.getConnectionPool().getLogAbandoned());
    }

    @Test
    public void testNoAccessToUnderlyingConnectionAllowed() throws Exception {
        assertFalse(ds.isAccessToUnderlyingConnectionAllowed());
        final Connection conn = getConnection();
        Connection dconn = ((DelegatingConnection<?>) conn).getDelegate();
        assertNull(dconn);
        dconn = ((DelegatingConnection<?>) conn).getInnermostDelegate();
        assertNull(dconn);
    }

    @Test
    public void testPoolCloseCheckedException() throws Exception {
        ds.setAccessToUnderlyingConnectionAllowed(true);
        final Connection conn = ds.getConnection();
        final TesterConnection tc = (TesterConnection) ((DelegatingConnection<?>) conn).getInnermostDelegate();
        conn.close();
        tc.setFailure(new SQLException("bang"));
        try {
            StackMessageLog.lock();
            StackMessageLog.clear();
            ds.close();
            final String message = StackMessageLog.popMessage();
            Assertions.assertNotNull(message);
            assertTrue(message.indexOf("bang") > 0);
        } catch (final SQLException ex) {
            assertTrue(ex.getMessage().indexOf("Cannot close") > 0);
            assertTrue(ex.getCause().getMessage().indexOf("bang") > 0);
        } finally {
            StackMessageLog.unLock();
        }
    }

    @Test
    public void testPoolCloseRTE() throws Exception {
        ds.setAccessToUnderlyingConnectionAllowed(true);
        final Connection conn = ds.getConnection();
        final TesterConnection tc = (TesterConnection) ((DelegatingConnection<?>) conn).getInnermostDelegate();
        conn.close();
        tc.setFailure(new IllegalStateException("boom"));
        try {
            StackMessageLog.lock();
            StackMessageLog.clear();
            ds.close();
            final String message = StackMessageLog.popMessage();
            Assertions.assertNotNull(message);
            assertTrue(message.indexOf("boom") > 0);
        } catch (final IllegalStateException ex) {
            assertTrue(ex.getMessage().indexOf("boom") > 0);
        } finally {
            StackMessageLog.unLock();
        }
    }

    @Override
    @Test
    public void testPooling() throws Exception {
        ds.setAccessToUnderlyingConnectionAllowed(true);
        super.testPooling();
    }

    @Test
    public void testPropertyTestOnReturn() throws Exception {
        ds.setValidationQuery("select 1 from dual");
        ds.setTestOnBorrow(false);
        ds.setTestWhileIdle(false);
        ds.setTestOnReturn(true);
        final Connection conn = ds.getConnection();
        assertNotNull(conn);
        assertFalse(ds.getConnectionPool().getTestOnBorrow());
        assertFalse(ds.getConnectionPool().getTestWhileIdle());
        assertTrue(ds.getConnectionPool().getTestOnReturn());
    }

    @Test
    public void testRestart() throws Exception {
        ds.setMaxTotal(2);
        ds.setTimeBetweenEvictionRunsMillis(100);
        ds.setNumTestsPerEvictionRun(2);
        ds.setMinEvictableIdleTimeMillis(60000);
        ds.setInitialSize(2);
        ds.setDefaultCatalog("foo");
        final Connection conn1 = ds.getConnection();
        Thread.sleep(200);
        ds.setDefaultCatalog("bar");
        ds.setInitialSize(1);
        ds.restart();
        assertEquals("bar", ds.getDefaultCatalog());
        assertEquals(1, ds.getInitialSize());
        ds.getLogWriter();
        assertEquals(0, ds.getNumActive());
        assertEquals(1, ds.getNumIdle());
        conn1.close();
        assertEquals(1, ds.getNumIdle());
        ds.close();
    }

    @Test
    public void testRollbackReadOnly() throws Exception {
        ds.setDefaultReadOnly(Boolean.TRUE);
        ds.setDefaultAutoCommit(Boolean.FALSE);

        final Connection conn = ds.getConnection();
        assertNotNull(conn);
        conn.close();
    }

    @Test
    public void testSetAutoCommitTrueOnClose() throws Exception {
        ds.setAccessToUnderlyingConnectionAllowed(true);
        ds.setDefaultAutoCommit(Boolean.FALSE);

        final Connection conn = getConnection();
        assertNotNull(conn);
        assertFalse(conn.getAutoCommit());

        final Connection dconn = ((DelegatingConnection<?>) conn).getInnermostDelegate();
        assertNotNull(dconn);
        assertFalse(dconn.getAutoCommit());

        conn.close();

        assertTrue(dconn.getAutoCommit());
    }

    @Test
    public void testSetProperties() {
        ds.setConnectionProperties("name1=value1;name2=value2;name3=value3");
        assertEquals(3, ds.getConnectionProperties().size());
        assertEquals("value1", ds.getConnectionProperties().getProperty("name1"));
        assertEquals("value2", ds.getConnectionProperties().getProperty("name2"));
        assertEquals("value3", ds.getConnectionProperties().getProperty("name3"));
        ds.setConnectionProperties("name1=value1;name2=value2");
        assertEquals(2, ds.getConnectionProperties().size());
        assertEquals("value1", ds.getConnectionProperties().getProperty("name1"));
        assertEquals("value2", ds.getConnectionProperties().getProperty("name2"));
        assertFalse(ds.getConnectionProperties().containsKey("name3"));
        ds.setConnectionProperties("name1=value1;name2");
        assertEquals(2, ds.getConnectionProperties().size());
        assertEquals("value1", ds.getConnectionProperties().getProperty("name1"));
        assertEquals("", ds.getConnectionProperties().getProperty("name2"));
        ds.setConnectionProperties("name1=value1;name2=");
        assertEquals(2, ds.getConnectionProperties().size());
        assertEquals("value1", ds.getConnectionProperties().getProperty("name1"));
        assertEquals("", ds.getConnectionProperties().getProperty("name2"));
        ds.setConnectionProperties("name1=value1");
        assertEquals(1, ds.getConnectionProperties().size());
        assertEquals("value1", ds.getConnectionProperties().getProperty("name1"));
        ds.setConnectionProperties("name1=value1;");
        assertEquals(1, ds.getConnectionProperties().size());
        assertEquals("value1", ds.getConnectionProperties().getProperty("name1"));
        ds.setConnectionProperties("name1");
        assertEquals(1, ds.getConnectionProperties().size());
        assertEquals("", ds.getConnectionProperties().getProperty("name1"));
        try {
            ds.setConnectionProperties(null);
            fail("Expected NullPointerException");
        } catch (final NullPointerException ignored) {
        }
    }

    @Test
    public void testSetValidationTestProperties() {
        assertTrue(ds.getTestOnBorrow());
        assertFalse(ds.getTestOnReturn());
        assertFalse(ds.getTestWhileIdle());
        ds.setTestOnBorrow(true);
        ds.setTestOnReturn(true);
        ds.setTestWhileIdle(true);
        assertTrue(ds.getTestOnBorrow());
        assertTrue(ds.getTestOnReturn());
        assertTrue(ds.getTestWhileIdle());
        ds.setTestOnBorrow(false);
        ds.setTestOnReturn(false);
        ds.setTestWhileIdle(false);
        assertFalse(ds.getTestOnBorrow());
        assertFalse(ds.getTestOnReturn());
        assertFalse(ds.getTestWhileIdle());
    }

    @Test
    public void testStart() throws Exception {
        ds.setAccessToUnderlyingConnectionAllowed(true);
        ds.setMaxTotal(2);
        final DelegatingConnection<?> conn1 = (DelegatingConnection<?>) ds.getConnection();
        final DelegatingConnection<?> conn2 = (DelegatingConnection<?>) ds.getConnection();
        final Connection inner1 = conn1.getInnermostDelegate();
        final Connection inner2 = conn2.getInnermostDelegate();
        assertFalse(inner2.isClosed());
        conn2.close();
        assertFalse(inner2.isClosed());
        ds.close();
        assertFalse(conn1.isClosed());
        assertTrue(inner2.isClosed());
        assertEquals(0, ds.getNumIdle());
        ds.start();
        final Connection conn3 = ds.getConnection();
        final Connection conn4 = ds.getConnection();
        conn3.close();
        conn4.close();
        conn1.close();
        assertTrue(inner1.isClosed());
    }

    @Test
    public void testStartInitializes() throws Exception {
        ds.setInitialSize(2);
        assertEquals(0, ds.getNumIdle());
        assertNull(ds.getRegisteredJmxName());
        ds.start();
        assertEquals(2, ds.getNumIdle());
        assertNotNull(ds.getRegisteredJmxName());
    }

    @Test
    public void testTransactionIsolationBehavior() throws Exception {
        try (Connection conn = getConnection()) {
            assertNotNull(conn);
            assertEquals(Connection.TRANSACTION_READ_COMMITTED, conn.getTransactionIsolation());
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
        }
        final Connection conn2 = getConnection();
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, conn2.getTransactionIsolation());
        final Connection conn3 = getConnection();
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, conn3.getTransactionIsolation());
        conn2.close();
        conn3.close();
    }

    @Test
    public void testUnwrap() throws Exception {
        assertSame(ds.unwrap(BasicDataSource.class), ds);
        assertSame(ds.unwrap(AutoCloseable.class), ds);
        assertThrows(SQLException.class, () -> ds.unwrap(String.class));
        assertThrows(SQLException.class, () -> ds.unwrap(null));
    }

    @Test
    public void testValidationQueryTimeoutNegative() throws Exception {
        ds.setTestOnBorrow(true);
        ds.setTestOnReturn(true);
        ds.setValidationQueryTimeout(-1);
        try (Connection ignored = ds.getConnection()) {
            assertNotNull(ignored);
        }
    }

    @Test
    public void testValidationQueryTimeoutSucceed() throws Exception {
        ds.setTestOnBorrow(true);
        ds.setTestOnReturn(true);
        ds.setValidationQueryTimeout(100);
        try (Connection ignored = ds.getConnection()) {
            assertNotNull(ignored);
        }
    }

    @Test
    public void testValidationQueryTimeoutZero() throws Exception {
        ds.setTestOnBorrow(true);
        ds.setTestOnReturn(true);
        ds.setValidationQueryTimeout(0);
        try (Connection ignored = ds.getConnection()) {
            assertNotNull(ignored);
        }
    }

    @Test
    public void testValidationQueryTimoutFail() {
        ds.setTestOnBorrow(true);
        ds.setValidationQueryTimeout(3);
        try (Connection ignored = ds.getConnection()) {
            fail("expected SQLException");
        } catch (final SQLException ex) {
            if (!ex.toString().contains("timeout")) {
                fail("expected timeout error message");
            }
        }
    }
}

@SuppressWarnings("unused")
class TesterConnectionDelayDriver extends TesterDriver {
    private static final String CONNECT_STRING = "jdbc:apache:commons:testerConnectionDelayDriver";

    //Checkstyle: stop method name check
    public TesterConnectionDelayDriver() {
    }
    //Checkstyle: resume method name check

    @Override
    public boolean acceptsURL(final String url) {
        return url.startsWith(CONNECT_STRING);
    }

    @Override
    public Connection connect(final String url, final Properties info) throws SQLException {
        final String[] parsedUrl = url.split(":");
        final int delay = Integer.parseInt(parsedUrl[parsedUrl.length - 1]);
        try {
            Thread.sleep(delay);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return super.connect(url, info);
    }
}

class TesterConnRequestCountDriver extends TesterDriver {
    private static final String CONNECT_STRING = "jdbc:apache:commons:testerConnRequestCountDriver";
    private static final AtomicInteger connectionRequestCount = new AtomicInteger(0);

    public static int getConnectionRequestCount() {
        return connectionRequestCount.get();
    }

    public static void initConnRequestCount() {
        connectionRequestCount.set(0);
    }

    //Checkstyle: stop method name check
    public TesterConnRequestCountDriver() {
    }
    //Checkstyle: resume method name check

    @Override
    public boolean acceptsURL(final String url) {
        return CONNECT_STRING.startsWith(url);
    }

    @Override
    public Connection connect(final String url, final Properties info) throws SQLException {
        connectionRequestCount.incrementAndGet();
        return super.connect(url, info);
    }
}
