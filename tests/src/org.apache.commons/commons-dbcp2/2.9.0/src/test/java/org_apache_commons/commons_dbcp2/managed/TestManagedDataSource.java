/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2.managed;

import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.Constants;
import org.apache.commons.dbcp2.DelegatingConnection;
import org.apache.commons.dbcp2.DriverConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.dbcp2.TestConnectionPool;
import org.apache.commons.dbcp2.TesterDriver;
import org.apache.commons.dbcp2.managed.LocalXAConnectionFactory;
import org.apache.commons.dbcp2.managed.ManagedConnection;
import org.apache.commons.dbcp2.managed.ManagedDataSource;
import org.apache.commons.dbcp2.managed.TransactionRegistry;
import org.apache.commons.dbcp2.managed.XAConnectionFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.geronimo.transaction.manager.TransactionManagerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.transaction.TransactionManager;
import java.sql.Connection;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"resource", "ConstantValue", "AssertBetweenInconvertibleTypes", "UnnecessaryLocalVariable"})
public class TestManagedDataSource extends TestConnectionPool {
    protected PoolingDataSource<PoolableConnection> ds;
    protected GenericObjectPool<PoolableConnection> pool;
    protected TransactionManager transactionManager;

    @Override
    protected Connection getConnection() throws Exception {
        return ds.getConnection();
    }

    @BeforeEach
    public void setUp() throws Exception {
        transactionManager = new TransactionManagerImpl();
        final Properties properties = new Properties();
        properties.setProperty(Constants.KEY_USER, "userName");
        properties.setProperty(Constants.KEY_PASSWORD, "password");
        final ConnectionFactory connectionFactory = new DriverConnectionFactory(new TesterDriver(), "jdbc:apache:commons:testdriver", properties);
        final XAConnectionFactory xaConnectionFactory = new LocalXAConnectionFactory(transactionManager, connectionFactory);
        final PoolableConnectionFactory factory = new PoolableConnectionFactory(xaConnectionFactory, null);
        factory.setValidationQuery("SELECT DUMMY FROM DUAL");
        factory.setDefaultReadOnly(Boolean.TRUE);
        factory.setDefaultAutoCommit(Boolean.TRUE);
        pool = new GenericObjectPool<>(factory);
        factory.setPool(pool);
        pool.setMaxTotal(getMaxTotal());
        pool.setMaxWaitMillis(getMaxWaitMillis());
        ds = new ManagedDataSource<>(pool, xaConnectionFactory.getTransactionRegistry());
        ds.setAccessToUnderlyingConnectionAllowed(true);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        pool.close();
        super.tearDown();
    }

    @Test
    public void testAccessToUnderlyingConnectionAllowed() throws Exception {
        ds.setAccessToUnderlyingConnectionAllowed(true);
        ManagedConnection<?> connection = (ManagedConnection<?>) newConnection();
        assertTrue(connection.isAccessToUnderlyingConnectionAllowed());
        assertNotNull(connection.getDelegate());
        assertNotNull(connection.getInnermostDelegate());
        connection.close();
        ds.setAccessToUnderlyingConnectionAllowed(false);
        connection = (ManagedConnection<?>) newConnection();
        assertFalse(connection.isAccessToUnderlyingConnectionAllowed());
        assertNull(connection.getDelegate());
        assertNull(connection.getInnermostDelegate());
        connection.close();
    }

    @Test
    public void testConnectionReturnOnCommit() throws Exception {
        transactionManager.begin();
        final DelegatingConnection<?> connectionA = (DelegatingConnection<?>) newConnection();
        connectionA.close();
        transactionManager.commit();
        assertEquals(1, pool.getBorrowedCount());
        assertEquals(1, pool.getReturnedCount());
        assertEquals(0, pool.getNumActive());
    }

    @Test
    public void testManagedConnectionEqualInnermost() throws Exception {
        ds.setAccessToUnderlyingConnectionAllowed(true);
        final DelegatingConnection<?> con = (DelegatingConnection<?>) ds.getConnection();
        final Connection inner = con.getInnermostDelegate();
        ds.setAccessToUnderlyingConnectionAllowed(false);
        final DelegatingConnection<Connection> con2 = new DelegatingConnection<>(inner);
        assertNotEquals(con2, con);
        assertTrue(con.innermostDelegateEquals(con2.getInnermostDelegate()));
        assertTrue(con2.innermostDelegateEquals(inner));
        assertNotEquals(con, con2);
    }

    @Test
    public void testManagedConnectionEqualsFail() throws Exception {
        final Connection con1 = ds.getConnection();
        final Connection con2 = ds.getConnection();
        assertNotEquals(con1, con2);
        con1.close();
        con2.close();
    }

    @Test
    public void testManagedConnectionEqualsNull() throws Exception {
        final Connection con1 = ds.getConnection();
        final Connection con2 = null;
        assertNotEquals(con2, con1);
        con1.close();
    }

    @Test
    public void testManagedConnectionEqualsReflexive() throws Exception {
        final Connection con = ds.getConnection();
        final Connection con2 = con;
        assertEquals(con2, con);
        assertEquals(con, con2);
        con.close();
    }

    @Test
    public void testManagedConnectionEqualsSameDelegate() throws Exception {
        final Connection[] c = new Connection[getMaxTotal()];
        for (int i = 0; i < c.length; i++) {
            c[i] = newConnection();
        }
        ((DelegatingConnection<?>) c[0]).getDelegate().close();
        final Connection con = newConnection();
        Assertions.assertNotEquals(c[0], con);
        Assertions.assertEquals(
                ((DelegatingConnection<?>) c[0]).getInnermostDelegateInternal(),
                ((DelegatingConnection<?>) con).getInnermostDelegateInternal());
        for (final Connection element : c) {
            element.close();
        }
    }

    @Test
    public void testManagedConnectionEqualsSameDelegateNoUnderlyingAccess() throws Exception {
        final Connection[] c = new Connection[getMaxTotal()];
        for (int i = 0; i < c.length; i++) {
            c[i] = newConnection();
        }
        ((DelegatingConnection<?>) c[0]).getDelegate().close();
        ds.setAccessToUnderlyingConnectionAllowed(false);
        final Connection con = newConnection();
        Assertions.assertNotEquals(c[0], con);
        Assertions.assertEquals(((DelegatingConnection<?>) c[0]).getInnermostDelegateInternal(),
                ((DelegatingConnection<?>) con).getInnermostDelegateInternal());
        for (final Connection element : c) {
            element.close();
        }
        ds.setAccessToUnderlyingConnectionAllowed(true);
    }

    @Test
    public void testManagedConnectionEqualsType() throws Exception {
        final Connection con1 = ds.getConnection();
        final Integer con2 = 0;
        assertNotEquals(con2, con1);
        con1.close();
    }

    @Test
    public void testNestedConnections() throws Exception {
        transactionManager.begin();
        final Connection c1 = newConnection();
        final Connection c2 = newConnection();
        transactionManager.commit();
        c1.close();
        c2.close();
    }

    @Test
    public void testSetNullTransactionRegistry() throws Exception {
        try (ManagedDataSource<?> ds = new ManagedDataSource<>(pool, null)) {
            assertThrows(NullPointerException.class, () -> ds.setTransactionRegistry(null));
        }
    }

    @Test()
    public void testSetTransactionRegistry() throws Exception {
        try (ManagedDataSource<?> ds = new ManagedDataSource<>(pool, null)) {
            ds.setTransactionRegistry(new TransactionRegistry(transactionManager));
        }
    }

    @Test
    public void testSetTransactionRegistryAlreadySet() {
        final ManagedDataSource<?> managed = (ManagedDataSource<?>) ds;
        assertThrows(IllegalStateException.class, () -> managed.setTransactionRegistry(null));
    }

    @Test
    public void testSharedConnection() throws Exception {
        final DelegatingConnection<?> connectionA = (DelegatingConnection<?>) newConnection();
        final DelegatingConnection<?> connectionB = (DelegatingConnection<?>) newConnection();
        assertNotEquals(connectionA, connectionB);
        assertNotEquals(connectionB, connectionA);
        assertFalse(connectionA.innermostDelegateEquals(connectionB.getInnermostDelegate()));
        assertFalse(connectionB.innermostDelegateEquals(connectionA.getInnermostDelegate()));
        connectionA.close();
        connectionB.close();
    }

    @Test
    public void testTransactionRegistryNotInitialized() throws Exception {
        try (ManagedDataSource<?> ds = new ManagedDataSource<>(pool, null)) {
            assertThrows(IllegalStateException.class, ds::getConnection);
        }
    }
}
