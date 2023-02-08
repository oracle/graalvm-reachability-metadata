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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@SuppressWarnings({"unused", "deprecation"})
public class TestPoolingDriver extends TestConnectionPool {
    private PoolingDriver driver;

    @Override
    protected Connection getConnection() throws Exception {
        return DriverManager.getConnection("jdbc:apache:commons:dbcp:test");
    }

    @BeforeEach
    public void setUp() throws Exception {
        final DriverConnectionFactory cf = new DriverConnectionFactory(new TesterDriver(), "jdbc:apache:commons:testdriver", null);
        final PoolableConnectionFactory pcf = new PoolableConnectionFactory(cf, null);
        pcf.setPoolStatements(true);
        pcf.setMaxOpenPreparedStatements(10);
        pcf.setValidationQuery("SELECT COUNT(*) FROM DUAL");
        pcf.setDefaultReadOnly(Boolean.FALSE);
        pcf.setDefaultAutoCommit(Boolean.TRUE);
        final GenericObjectPoolConfig<PoolableConnection> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(getMaxTotal());
        poolConfig.setMaxWaitMillis(getMaxWaitMillis());
        poolConfig.setMinIdle(10);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setTimeBetweenEvictionRunsMillis(10000L);
        poolConfig.setNumTestsPerEvictionRun(5);
        poolConfig.setMinEvictableIdleTimeMillis(5000L);
        final GenericObjectPool<PoolableConnection> pool = new GenericObjectPool<>(pcf, poolConfig);
        pcf.setPool(pool);
        Assertions.assertNotNull(pcf);
        driver = new PoolingDriver(true);
        driver.registerPool("test", pool);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        driver.closePool("test");
        super.tearDown();
    }

    @Test
    public void test1() {
        final ConnectionFactory connectionFactory = new DriverManagerConnectionFactory("jdbc:some:connect:string", "userName", "password");
        final PoolableConnectionFactory pcf =
                new PoolableConnectionFactory(connectionFactory, null);
        pcf.setDefaultReadOnly(Boolean.FALSE);
        pcf.setDefaultAutoCommit(Boolean.TRUE);
        final GenericObjectPool<PoolableConnection> connectionPool =
                new GenericObjectPool<>(pcf);
        pcf.setPool(connectionPool);
        final DataSource ds = new PoolingDataSource<>(connectionPool);
        Assertions.assertNotNull(ds);
    }

    @Test
    public void test2() {
        final ConnectionFactory connectionFactory = new DriverManagerConnectionFactory("jdbc:some:connect:string", "userName", "password");
        final PoolableConnectionFactory pcf =
                new PoolableConnectionFactory(connectionFactory, null);
        pcf.setDefaultReadOnly(Boolean.FALSE);
        pcf.setDefaultAutoCommit(Boolean.TRUE);
        final GenericObjectPool<PoolableConnection> connectionPool = new GenericObjectPool<>(pcf);
        final PoolingDriver driver2 = new PoolingDriver();
        driver2.registerPool("example", connectionPool);
    }

    @Test
    public void testClosePool() throws Exception {
        final Connection conn = DriverManager.getConnection("jdbc:apache:commons:dbcp:test");
        Assertions.assertNotNull(conn);
        conn.close();
        final PoolingDriver driver2 = (PoolingDriver) DriverManager.getDriver("jdbc:apache:commons:dbcp:");
        driver2.closePool("test");
        try (Connection c = DriverManager.getConnection("jdbc:apache:commons:dbcp:test")) {
            Assertions.fail("expected SQLException");
        } catch (final SQLException e) {
        }
    }

    @Test
    public void testInvalidateConnection() throws Exception {
        final Connection conn = DriverManager.getConnection("jdbc:apache:commons:dbcp:test");
        Assertions.assertNotNull(conn);
        final ObjectPool<?> pool = driver.getConnectionPool("test");
        Assertions.assertEquals(1, pool.getNumActive());
        Assertions.assertEquals(0, pool.getNumIdle());
        final PoolingDriver driver2 = (PoolingDriver) DriverManager.getDriver("jdbc:apache:commons:dbcp:");
        driver2.invalidateConnection(conn);
        Assertions.assertEquals(0, pool.getNumActive());
        Assertions.assertEquals(0, pool.getNumIdle());
        Assertions.assertTrue(conn.isClosed());
    }

    @Test
    public void testLogWriter() {
        final PrintStream ps = new PrintStream(new ByteArrayOutputStream(), false, StandardCharsets.UTF_8);
        final PrintWriter pw = new PrintWriter(new OutputStreamWriter(new ByteArrayOutputStream(), StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new ByteArrayOutputStream(), false, StandardCharsets.UTF_8));
        SQLException ex;
        DriverManager.setLogWriter(pw);
        ex = new SQLException("A", new Exception("a"));
        ex.printStackTrace();
        ex.printStackTrace(ps);
        ex.printStackTrace(pw);
        ex = new SQLException("B");
        ex.printStackTrace();
        ex.printStackTrace(ps);
        ex.printStackTrace(pw);
        ex = new SQLException(null, new Exception("c"));
        ex.printStackTrace();
        ex.printStackTrace(ps);
        ex.printStackTrace(pw);
        ex = new SQLException((String) null);
        ex.printStackTrace();
        ex.printStackTrace(ps);
        ex.printStackTrace(pw);
        DriverManager.setLogWriter(null);
        ex = new SQLException("A", new Exception("a"));
        ex.printStackTrace();
        ex.printStackTrace(ps);
        ex.printStackTrace(pw);
        ex = new SQLException("B");
        ex.printStackTrace();
        ex.printStackTrace(ps);
        ex.printStackTrace(pw);
        ex = new SQLException(null, new Exception("c"));
        ex.printStackTrace();
        ex.printStackTrace(ps);
        ex.printStackTrace(pw);
        ex = new SQLException((String) null);
        ex.printStackTrace();
        ex.printStackTrace(ps);
        ex.printStackTrace(pw);
    }

    @Test
    public void testReportedBug12400() throws Exception {
        final GenericObjectPoolConfig<PoolableConnection> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(70);
        config.setMaxWaitMillis(60000);
        config.setMaxIdle(10);
        final ConnectionFactory connectionFactory = new DriverManagerConnectionFactory("jdbc:apache:commons:testdriver",
                "userName", "password");
        final PoolableConnectionFactory poolableConnectionFactory =
                new PoolableConnectionFactory(connectionFactory, null);
        poolableConnectionFactory.setDefaultReadOnly(Boolean.FALSE);
        poolableConnectionFactory.setDefaultAutoCommit(Boolean.TRUE);
        final ObjectPool<PoolableConnection> connectionPool = new GenericObjectPool<>(poolableConnectionFactory,
                config);
        poolableConnectionFactory.setPool(connectionPool);
        Assertions.assertNotNull(poolableConnectionFactory);
        final PoolingDriver driver2 = new PoolingDriver();
        driver2.registerPool("neusoftim", connectionPool);
        final Connection[] conn = new Connection[25];
        for (int i = 0; i < 25; i++) {
            conn[i] = DriverManager.getConnection("jdbc:apache:commons:dbcp:neusoftim");
            for (int j = 0; j < i; j++) {
                Assertions.assertNotSame(conn[j], conn[i]);
                Assertions.assertNotEquals(conn[j], conn[i]);
            }
        }
        for (int i = 0; i < 25; i++) {
            conn[i].close();
        }
    }

    @Test
    public void testReportedBug28912() throws Exception {
        final Connection conn1 = getConnection();
        Assertions.assertNotNull(conn1);
        Assertions.assertFalse(conn1.isClosed());
        conn1.close();
        final Connection conn2 = getConnection();
        Assertions.assertNotNull(conn2);
        Assertions.assertTrue(conn1.isClosed());
        Assertions.assertFalse(conn2.isClosed());
        conn1.close();
        Assertions.assertTrue(conn1.isClosed());
        Assertions.assertFalse(conn2.isClosed());
    }
}
