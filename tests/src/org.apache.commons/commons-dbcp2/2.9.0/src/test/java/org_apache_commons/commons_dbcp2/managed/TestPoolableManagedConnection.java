/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org_apache_commons.commons_dbcp2.managed;

import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.Constants;
import org.apache.commons.dbcp2.DriverConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.TesterDriver;
import org.apache.commons.dbcp2.managed.LocalXAConnectionFactory;
import org.apache.commons.dbcp2.managed.PoolableManagedConnection;
import org.apache.commons.dbcp2.managed.TransactionRegistry;
import org.apache.commons.dbcp2.managed.XAConnectionFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.geronimo.transaction.manager.TransactionManagerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.transaction.TransactionManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;


@SuppressWarnings("FieldCanBeLocal")
public class TestPoolableManagedConnection {
    private TransactionManager transactionManager;
    private TransactionRegistry transactionRegistry;
    private GenericObjectPool<PoolableConnection> pool;
    private Connection conn;
    private PoolableManagedConnection poolableManagedConnection;

    @BeforeEach
    public void setUp() throws Exception {
        transactionManager = new TransactionManagerImpl();
        final Properties properties = new Properties();
        properties.setProperty(Constants.KEY_USER, "userName");
        properties.setProperty(Constants.KEY_PASSWORD, "password");
        final ConnectionFactory connectionFactory = new DriverConnectionFactory(new TesterDriver(), "jdbc:apache:commons:testdriver", properties);
        final XAConnectionFactory xaConnectionFactory = new LocalXAConnectionFactory(transactionManager, connectionFactory);
        transactionRegistry = xaConnectionFactory.getTransactionRegistry();
        final PoolableConnectionFactory factory = new PoolableConnectionFactory(xaConnectionFactory, null);
        factory.setValidationQuery("SELECT DUMMY FROM DUAL");
        factory.setDefaultReadOnly(Boolean.TRUE);
        factory.setDefaultAutoCommit(Boolean.TRUE);
        pool = new GenericObjectPool<>(factory);
        factory.setPool(pool);
        pool.setMaxTotal(10);
        pool.setMaxWaitMillis(100);
    }

    @AfterEach
    public void tearDown() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
        if (pool != null && !pool.isClosed()) {
            pool.close();
        }
    }

    @Test
    public void testManagedConnection() throws Exception {
        assertEquals(0, pool.getNumActive());
        conn = pool.borrowObject();
        assertEquals(1, pool.getNumActive());
        poolableManagedConnection = new PoolableManagedConnection(transactionRegistry, conn, pool);
        poolableManagedConnection.close();
        assertEquals(1, pool.getNumActive());
        conn.close();
        assertEquals(0, pool.getNumActive());
    }

    @Test
    public void testPoolableConnection() throws Exception {
        conn = pool.borrowObject();
        assertNotNull(transactionRegistry.getXAResource(conn));
        poolableManagedConnection = new PoolableManagedConnection(transactionRegistry, conn, pool);
        poolableManagedConnection.close();
        assertNotNull(transactionRegistry.getXAResource(conn));
    }

    @Test
    public void testReallyClose() throws Exception {
        assertEquals(0, pool.getNumActive());
        conn = pool.borrowObject();
        assertEquals(1, pool.getNumActive());
        assertNotNull(transactionRegistry.getXAResource(conn));
        poolableManagedConnection = new PoolableManagedConnection(transactionRegistry, conn, pool);
        poolableManagedConnection.close();
        assertNotNull(transactionRegistry.getXAResource(conn));
        assertEquals(1, pool.getNumActive());
        poolableManagedConnection.reallyClose();
        try {
            assertNull(transactionRegistry.getXAResource(conn));
            fail("Transaction registry was supposed to be empty now");
        } catch (final SQLException ignored) {
        }
        assertEquals(0, pool.getNumActive());
    }
}
