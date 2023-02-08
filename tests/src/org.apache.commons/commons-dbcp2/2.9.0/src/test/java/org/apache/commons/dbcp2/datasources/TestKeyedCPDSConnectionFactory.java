/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package org.apache.commons.dbcp2.datasources;

import org_apache_commons.commons_dbcp2.datasources.ConnectionPoolDataSourceProxy;
import org_apache_commons.commons_dbcp2.datasources.PooledConnectionProxy;
import org.apache.commons.dbcp2.cpdsadapter.DriverAdapterCPDS;
import org.apache.commons.pool2.KeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.PooledConnection;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings("deprecation")
public class TestKeyedCPDSConnectionFactory {

    protected ConnectionPoolDataSourceProxy cpds;

    @BeforeEach
    public void setUp() throws Exception {
        cpds = new ConnectionPoolDataSourceProxy(new DriverAdapterCPDS());
        final DriverAdapterCPDS delegate = (DriverAdapterCPDS) cpds.getDelegate();
        delegate.setDriver("org.apache.commons.dbcp2.TesterDriver");
        delegate.setUrl("jdbc:apache:commons:testdriver");
        delegate.setUser("userName");
        delegate.setPassword("password");
    }

    @Test
    public void testConnectionErrorCleanup() throws Exception {
        final UserPassKey key = new UserPassKey("userName", "password");
        final KeyedCPDSConnectionFactory factory =
                new KeyedCPDSConnectionFactory(cpds, null, -1, false);
        final KeyedObjectPool<UserPassKey, PooledConnectionAndInfo> pool = new GenericKeyedObjectPool<>(factory);
        factory.setPool(pool);
        final PooledConnection pcon1 =
                pool.borrowObject(key)
                        .getPooledConnection();
        final Connection con1 = pcon1.getConnection();
        final PooledConnection pcon2 = pool.borrowObject(key).getPooledConnection();
        assertEquals(2, pool.getNumActive(key));
        assertEquals(0, pool.getNumIdle(key));
        final PooledConnectionProxy pc = (PooledConnectionProxy) pcon1;
        assertTrue(pc.getListeners().contains(factory));
        pc.throwConnectionError();
        assertEquals(1, pool.getNumActive(key));
        assertEquals(0, pool.getNumIdle(key));
        pc.throwConnectionError();
        assertEquals(1, pool.getNumActive(key));
        assertEquals(0, pool.getNumIdle(key));
        final PooledConnection pcon3 = pool.borrowObject(key).getPooledConnection();
        assertNotEquals(pcon3, pcon1);
        assertFalse(pc.getListeners().contains(factory));
        assertEquals(2, pool.getNumActive(key));
        assertEquals(0, pool.getNumIdle(key));
        pcon2.getConnection().close();
        pcon3.getConnection().close();
        assertEquals(2, pool.getNumIdle(key));
        assertEquals(0, pool.getNumActive(key));
        try {
            pc.getConnection();
            fail("Expecting SQLException using closed PooledConnection");
        } catch (final SQLException ex) {
        }
        con1.close();
        assertEquals(2, pool.getNumIdle(key));
        assertEquals(0, pool.getNumActive(key));
        factory.getPool().clear();
        assertEquals(0, pool.getNumIdle(key));
    }

    @Test
    public void testNullValidationQuery() throws Exception {
        final UserPassKey key = new UserPassKey("userName", "password");
        final KeyedCPDSConnectionFactory factory =
                new KeyedCPDSConnectionFactory(cpds, null, -1, false);
        final GenericKeyedObjectPool<UserPassKey, PooledConnectionAndInfo> pool = new GenericKeyedObjectPool<>(factory);
        factory.setPool(pool);
        pool.setTestOnBorrow(true);
        final PooledConnection pcon = pool.borrowObject(key).getPooledConnection();
        final Connection con = pcon.getConnection();
        con.close();
    }

    @Test
    public void testSharedPoolDSDestroyOnReturn() throws Exception {
        final SharedPoolDataSource ds = new SharedPoolDataSource();
        ds.setConnectionPoolDataSource(cpds);
        ds.setMaxTotal(10);
        ds.setDefaultMaxWaitMillis(50);
        ds.setDefaultMaxIdle(2);
        final Connection conn1 = ds.getConnection("userName", "password");
        final Connection conn2 = ds.getConnection("userName", "password");
        final Connection conn3 = ds.getConnection("userName", "password");
        assertEquals(3, ds.getNumActive());
        conn1.close();
        assertEquals(1, ds.getNumIdle());
        conn2.close();
        assertEquals(2, ds.getNumIdle());
        conn3.close();
        assertEquals(2, ds.getNumIdle());
        ds.close();
    }
}
