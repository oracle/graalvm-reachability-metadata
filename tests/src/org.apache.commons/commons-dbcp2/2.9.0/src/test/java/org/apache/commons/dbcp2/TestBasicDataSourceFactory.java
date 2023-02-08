/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2;

import org_apache_commons.commons_dbcp2.StackMessageLog;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServer;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("deprecation")
public class TestBasicDataSourceFactory {
    private void checkConnectionPoolProperties(final GenericObjectPool<PoolableConnection> cp) {
        assertEquals(10, cp.getMaxTotal());
        assertEquals(8, cp.getMaxIdle());
        assertEquals(0, cp.getMinIdle());
        assertEquals(500, cp.getMaxWaitMillis());
        assertEquals(5, cp.getNumIdle());
        assertTrue(cp.getTestOnBorrow());
        assertFalse(cp.getTestOnReturn());
        assertEquals(1000, cp.getTimeBetweenEvictionRunsMillis());
        assertEquals(2000, cp.getMinEvictableIdleTimeMillis());
        assertEquals(3000, cp.getSoftMinEvictableIdleTimeMillis());
        assertEquals(2, cp.getNumTestsPerEvictionRun());
        assertTrue(cp.getTestWhileIdle());
        assertTrue(cp.getRemoveAbandonedOnBorrow());
        assertTrue(cp.getRemoveAbandonedOnMaintenance());
        assertEquals(3000, cp.getRemoveAbandonedTimeout());
        assertTrue(cp.getLogAbandoned());
        assertTrue(cp.getLifo());
    }

    private void checkDataSourceProperties(final BasicDataSource ds) throws Exception {
        assertEquals("org.apache.commons.dbcp2.TesterDriver", ds.getDriverClassName());
        assertEquals("jdbc:apache:commons:testdriver", ds.getUrl());
        assertEquals(10, ds.getMaxTotal());
        assertEquals(8, ds.getMaxIdle());
        assertEquals(0, ds.getMinIdle());
        assertEquals(500, ds.getMaxWaitMillis());
        assertEquals(5, ds.getInitialSize());
        assertEquals(5, ds.getNumIdle());
        assertEquals(Boolean.TRUE, ds.getDefaultAutoCommit());
        assertEquals(Boolean.FALSE, ds.getDefaultReadOnly());
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, ds.getDefaultTransactionIsolation());
        assertEquals("test", ds.getDefaultCatalog());
        assertEquals("testSchema", ds.getDefaultSchema());
        assertTrue(ds.getTestOnBorrow());
        assertFalse(ds.getTestOnReturn());
        assertEquals("userName", ds.getUsername());
        assertEquals("password", ds.getPassword());
        assertEquals("SELECT DUMMY FROM DUAL", ds.getValidationQuery());
        assertEquals(100, ds.getValidationQueryTimeout());
        assertEquals(2, ds.getConnectionInitSqls().size());
        assertEquals("SELECT 1", ds.getConnectionInitSqls().get(0));
        assertEquals("SELECT 2", ds.getConnectionInitSqls().get(1));
        assertEquals(1000, ds.getTimeBetweenEvictionRunsMillis());
        assertEquals(2000, ds.getMinEvictableIdleTimeMillis());
        assertEquals(3000, ds.getSoftMinEvictableIdleTimeMillis());
        assertEquals(2, ds.getNumTestsPerEvictionRun());
        assertTrue(ds.getTestWhileIdle());
        assertTrue(ds.isAccessToUnderlyingConnectionAllowed());
        assertTrue(ds.getRemoveAbandonedOnBorrow());
        assertTrue(ds.getRemoveAbandonedOnMaintenance());
        assertEquals(3000, ds.getRemoveAbandonedTimeout());
        assertTrue(ds.getLogAbandoned());
        assertTrue(ds.getAbandonedUsageTracking());
        assertTrue(ds.isPoolPreparedStatements());
        assertTrue(ds.isClearStatementPoolOnReturn());
        assertEquals(10, ds.getMaxOpenPreparedStatements());
        assertTrue(ds.getLifo());
        assertTrue(ds.getFastFailValidation());
        assertTrue(ds.getDisconnectionSqlCodes().contains("XXX"));
        assertTrue(ds.getDisconnectionSqlCodes().contains("YYY"));
        assertEquals("org.apache.commons.dbcp2:name=test", ds.getJmxName());
        final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        mbs.unregisterMBean(ds.getRegisteredJmxName());
    }

    private Properties getTestProperties() {
        final Properties properties = new Properties();
        properties.setProperty("driverClassName", "org.apache.commons.dbcp2.TesterDriver");
        properties.setProperty("url", "jdbc:apache:commons:testdriver");
        properties.setProperty("maxTotal", "10");
        properties.setProperty("maxIdle", "8");
        properties.setProperty("minIdle", "0");
        properties.setProperty("maxWaitMillis", "500");
        properties.setProperty("initialSize", "5");
        properties.setProperty("defaultAutoCommit", "true");
        properties.setProperty("defaultReadOnly", "false");
        properties.setProperty("defaultTransactionIsolation", "READ_COMMITTED");
        properties.setProperty("defaultCatalog", "test");
        properties.setProperty("defaultSchema", "testSchema");
        properties.setProperty("testOnBorrow", "true");
        properties.setProperty("testOnReturn", "false");
        properties.setProperty("username", "userName");
        properties.setProperty("password", "password");
        properties.setProperty("validationQuery", "SELECT DUMMY FROM DUAL");
        properties.setProperty("validationQueryTimeout", "100");
        properties.setProperty("connectionInitSqls", "SELECT 1;SELECT 2");
        properties.setProperty("timeBetweenEvictionRunsMillis", "1000");
        properties.setProperty("minEvictableIdleTimeMillis", "2000");
        properties.setProperty("softMinEvictableIdleTimeMillis", "3000");
        properties.setProperty("numTestsPerEvictionRun", "2");
        properties.setProperty("testWhileIdle", "true");
        properties.setProperty("accessToUnderlyingConnectionAllowed", "true");
        properties.setProperty("removeAbandonedOnBorrow", "true");
        properties.setProperty("removeAbandonedOnMaintenance", "true");
        properties.setProperty("removeAbandonedTimeout", "3000");
        properties.setProperty("logAbandoned", "true");
        properties.setProperty("abandonedUsageTracking", "true");
        properties.setProperty("poolPreparedStatements", "true");
        properties.setProperty("clearStatementPoolOnReturn", "true");
        properties.setProperty("maxOpenPreparedStatements", "10");
        properties.setProperty("lifo", "true");
        properties.setProperty("fastFailValidation", "true");
        properties.setProperty("disconnectionSqlCodes", "XXX,YYY");
        properties.setProperty("jmxName", "org.apache.commons.dbcp2:name=test");
        return properties;
    }

    @Test
    public void testAllProperties() throws Exception {
        try {
            StackMessageLog.lock();
            StackMessageLog.clear();
            final Reference ref = new Reference("javax.sql.DataSource", BasicDataSourceFactory.class.getName(), null);
            final Properties properties = getTestProperties();
            properties.entrySet().stream().map(entry -> new StringRefAddr((String) entry.getKey(), (String) entry.getValue())).forEach(ref::add);
            final BasicDataSourceFactory basicDataSourceFactory = new BasicDataSourceFactory();
            final BasicDataSource ds = (BasicDataSource) basicDataSourceFactory.getObjectInstance(ref, null, null, null);
            checkDataSourceProperties(ds);
            checkConnectionPoolProperties(ds.getConnectionPool());
            final List<String> messages = StackMessageLog.getAll();
            assertEquals(0, messages.size());
        } finally {
            StackMessageLog.clear();
            StackMessageLog.unLock();
        }
    }

    @Test
    public void testNoProperties() throws Exception {
        final Properties properties = new Properties();
        final BasicDataSource ds = BasicDataSourceFactory.createDataSource(properties);
        assertNotNull(ds);
    }

    @Test
    public void testProperties() throws Exception {
        final BasicDataSource ds = BasicDataSourceFactory.createDataSource(getTestProperties());
        checkDataSourceProperties(ds);
    }

    @Test
    public void testValidateProperties() throws Exception {
        try {
            StackMessageLog.lock();
            StackMessageLog.clear();
            final Reference ref = new Reference("javax.sql.DataSource", BasicDataSourceFactory.class.getName(), null);
            ref.add(new StringRefAddr("foo", "bar"));
            ref.add(new StringRefAddr("maxWait", "100"));
            ref.add(new StringRefAddr("driverClassName", "org.apache.commons.dbcp2.TesterDriver"));
            final BasicDataSourceFactory basicDataSourceFactory = new BasicDataSourceFactory();
            basicDataSourceFactory.getObjectInstance(ref, null, null, null);
            final List<String> messages = StackMessageLog.getAll();
            assertEquals(2, messages.size(), messages.toString());
            messages.forEach(message -> {
                if (message.contains("maxWait")) {
                    assertTrue(message.contains("use maxWaitMillis"));
                } else {
                    assertTrue(message.contains("foo"));
                    assertTrue(message.contains("Ignoring unknown property"));
                }
            });
        } finally {
            StackMessageLog.clear();
            StackMessageLog.unLock();
        }
    }
}
