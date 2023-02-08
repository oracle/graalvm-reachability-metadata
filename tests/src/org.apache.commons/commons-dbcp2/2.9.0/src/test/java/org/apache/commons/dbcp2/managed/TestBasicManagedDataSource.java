/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2.managed;

import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionManagerImple;
import com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.TestBasicDataSource;
import org.apache.geronimo.transaction.manager.TransactionManagerImpl;
import org.h2.Driver;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.XADataSource;
import javax.transaction.Synchronization;
import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.xa.XAException;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class TestBasicManagedDataSource extends TestBasicDataSource {

    @Override
    protected BasicDataSource createDataSource() throws Exception {
        final BasicManagedDataSource basicManagedDataSource = new BasicManagedDataSource();
        final TransactionManagerImpl transactionManager = new TransactionManagerImpl();
        basicManagedDataSource.setTransactionManager(transactionManager);
        basicManagedDataSource.setTransactionSynchronizationRegistry(transactionManager);
        return basicManagedDataSource;
    }

    @Test
    public void testCreateXaDataSourceNewInstance() throws SQLException, XAException {
        try (BasicManagedDataSource basicManagedDataSource = new BasicManagedDataSource()) {
            basicManagedDataSource.setXADataSource(JdbcDataSource.class.getCanonicalName());
            basicManagedDataSource.setDriverClassName(Driver.class.getName());
            basicManagedDataSource.setTransactionManager(new TransactionManagerImpl());
            assertNotNull(basicManagedDataSource.createConnectionFactory());
        }
    }

    @Test
    public void testCreateXaDataSourceNoInstanceSetAndNoDataSource() throws SQLException, XAException {
        try (BasicManagedDataSource basicManagedDataSource = new BasicManagedDataSource()) {
            basicManagedDataSource.setDriverClassName("org.apache.commons.dbcp2.TesterDriver");
            basicManagedDataSource.setUrl("jdbc:apache:commons:testdriver");
            basicManagedDataSource.setTransactionManager(new TransactionManagerImpl());
            assertNotNull(basicManagedDataSource.createConnectionFactory());
        }
    }

    @Test
    public void testReallyClose() throws Exception {
        try (BasicManagedDataSource basicManagedDataSource = new BasicManagedDataSource()) {
            basicManagedDataSource.setTransactionManager(new TransactionManagerImpl());
            basicManagedDataSource.setDriverClassName("org.apache.commons.dbcp2.TesterDriver");
            basicManagedDataSource.setUrl("jdbc:apache:commons:testdriver");
            basicManagedDataSource.setUsername("userName");
            basicManagedDataSource.setPassword("password");
            basicManagedDataSource.setMaxIdle(1);
            final ManagedConnection<?> conn = (ManagedConnection<?>) basicManagedDataSource.getConnection();
            assertNotNull(basicManagedDataSource.getTransactionRegistry().getXAResource(conn));
            final ManagedConnection<?> conn2 = (ManagedConnection<?>) basicManagedDataSource.getConnection();
            conn2.close();
            conn.close();
            try {
                basicManagedDataSource.getTransactionRegistry().getXAResource(conn);
                fail("Expecting SQLException - XAResources orphaned");
            } catch (final SQLException ex) {
            }
            conn2.close();
        }
    }

    @Test
    public void testRuntimeExceptionsAreRethrown() throws SQLException, XAException {
        try (BasicManagedDataSource basicManagedDataSource = new BasicManagedDataSource()) {
            basicManagedDataSource.setTransactionManager(new TransactionManagerImpl());
            basicManagedDataSource.setDriverClassName("org.apache.commons.dbcp2.TesterDriver");
            basicManagedDataSource.setUrl("jdbc:apache:commons:testdriver");
            basicManagedDataSource.setUsername("userName");
            basicManagedDataSource.setPassword("password");
            basicManagedDataSource.setMaxIdle(1);
            assertThrows(NullPointerException.class, () -> basicManagedDataSource.createPoolableConnectionFactory(null));
        }
    }

    @Test
    public void testSetDriverName() throws SQLException {
        try (BasicManagedDataSource basicManagedDataSource = new BasicManagedDataSource()) {
            basicManagedDataSource.setDriverClassName("adams");
            assertEquals("adams", basicManagedDataSource.getDriverClassName());
            basicManagedDataSource.setDriverClassName(null);
            assertNull(basicManagedDataSource.getDriverClassName());
        }
    }

    @Test
    public void testSetNullXaDataSourceInstance() throws SQLException, XAException {
        try (BasicManagedDataSource basicManagedDataSource = new BasicManagedDataSource()) {
            basicManagedDataSource.setTransactionManager(new TransactionManagerImpl());
            basicManagedDataSource.setDriverClassName("org.apache.commons.dbcp2.TesterDriver");
            basicManagedDataSource.setUrl("jdbc:apache:commons:testdriver");
            basicManagedDataSource.setUsername("userName");
            basicManagedDataSource.setPassword("password");
            basicManagedDataSource.setMaxIdle(1);
            basicManagedDataSource.setXaDataSourceInstance(null);
            assertNull(basicManagedDataSource.getXaDataSourceInstance());
        }
    }

    @Test
    public void testSetRollbackOnlyBeforeGetConnectionDoesNotLeak() throws Exception {
        final TransactionManager transactionManager = ((BasicManagedDataSource) ds).getTransactionManager();
        final int n = 3;
        ds.setMaxIdle(n);
        ds.setMaxTotal(n);
        for (int i = 0; i <= n; i++) {
            transactionManager.begin();
            transactionManager.setRollbackOnly();
            final Connection conn = getConnection();
            assertNotNull(conn);
            conn.close();
            transactionManager.rollback();
        }

        assertEquals(0, ds.getNumActive());
        assertEquals(1, ds.getNumIdle());
    }

    @Test
    public void testSetXaDataSourceInstance() throws SQLException, XAException {
        try (BasicManagedDataSource basicManagedDataSource = new BasicManagedDataSource()) {
            basicManagedDataSource.setTransactionManager(new TransactionManagerImpl());
            basicManagedDataSource.setDriverClassName("org.apache.commons.dbcp2.TesterDriver");
            basicManagedDataSource.setUrl("jdbc:apache:commons:testdriver");
            basicManagedDataSource.setUsername("userName");
            basicManagedDataSource.setPassword("password");
            basicManagedDataSource.setMaxIdle(1);
            basicManagedDataSource.setXaDataSourceInstance(new JdbcDataSource());
            assertNotNull(basicManagedDataSource.createConnectionFactory());
        }
    }

    @Test
    public void testTransactionManagerNotSet() throws SQLException {
        try (BasicManagedDataSource basicManagedDataSource = new BasicManagedDataSource()) {
            assertThrows(SQLException.class, basicManagedDataSource::createConnectionFactory);
        }
    }

    @Test
    public void testTransactionSynchronizationRegistry() throws Exception {
        try (BasicManagedDataSource basicManagedDataSource = new BasicManagedDataSource()) {
            basicManagedDataSource.setTransactionManager(new TransactionManagerImple());
            final TransactionSynchronizationRegistry tsr = new TransactionSynchronizationRegistryImple();
            basicManagedDataSource.setTransactionSynchronizationRegistry(tsr);
            final JdbcDataSource xaDataSource = new JdbcDataSource();
            xaDataSource.setUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
            basicManagedDataSource.setXaDataSourceInstance(xaDataSource);
            basicManagedDataSource.setMaxIdle(1);
            final TransactionManager tm = basicManagedDataSource.getTransactionManager();
            tm.begin();
            tsr.registerInterposedSynchronization(new Synchronization() {
                @Override
                public void afterCompletion(final int i) {
                }

                @Override
                public void beforeCompletion() {
                    Connection connection = null;
                    try {
                        connection = basicManagedDataSource.getConnection();
                        assertNotNull(connection);
                    } catch (final SQLException e) {
                        fail(e.getMessage());
                    } finally {
                        if (connection != null) {
                            try {
                                connection.close();
                            } catch (final SQLException e) {
                                fail(e.getMessage());
                            }
                        }
                    }
                }
            });
            tm.commit();
        }
    }

    @Test
    public void testXADataSource() throws SQLException {
        try (BasicManagedDataSource basicManagedDataSource = new BasicManagedDataSource()) {
            basicManagedDataSource.setXADataSource("anything");
            assertEquals("anything", basicManagedDataSource.getXADataSource());
        }
    }

    @Test
    public void testXaDataSourceInstance() throws SQLException {
        try (BasicManagedDataSource basicManagedDataSource = new BasicManagedDataSource()) {
            final XADataSource ds = new JdbcDataSource();
            basicManagedDataSource.setXaDataSourceInstance(ds);
            assertEquals(ds, basicManagedDataSource.getXaDataSourceInstance());
        }
    }
}
