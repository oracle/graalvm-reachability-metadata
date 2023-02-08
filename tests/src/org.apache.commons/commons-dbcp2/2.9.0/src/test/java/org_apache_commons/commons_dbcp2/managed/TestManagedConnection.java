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
import org.apache.commons.dbcp2.TesterDriver;
import org.apache.commons.dbcp2.managed.LocalXAConnectionFactory;
import org.apache.commons.dbcp2.managed.ManagedDataSource;
import org.apache.commons.dbcp2.managed.TransactionContext;
import org.apache.commons.dbcp2.managed.TransactionRegistry;
import org.apache.commons.dbcp2.managed.XAConnectionFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.geronimo.transaction.manager.TransactionManagerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAResource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestManagedConnection {
    private class UncooperativeLocalXAConnectionFactory extends LocalXAConnectionFactory {
        UncooperativeLocalXAConnectionFactory(final TransactionManager transactionManager, final ConnectionFactory connectionFactory) {
            super(transactionManager, connectionFactory);
            try {
                final Field field = LocalXAConnectionFactory.class.getDeclaredField("transactionRegistry");
                field.setAccessible(true);
                field.set(this, new UncooperativeTransactionRegistry(transactionManager));
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class UncooperativeTransaction implements Transaction {
        private final Transaction wrappedTransaction;

        UncooperativeTransaction(final Transaction transaction) {
            this.wrappedTransaction = transaction;
        }

        @Override
        public void commit()
                throws HeuristicMixedException, HeuristicRollbackException, RollbackException, SecurityException,
                SystemException {
            wrappedTransaction.commit();
        }

        @Override
        public boolean delistResource(final XAResource arg0, final int arg1)
                throws IllegalStateException, SystemException {
            return wrappedTransaction.delistResource(arg0, arg1);
        }

        @Override
        public synchronized boolean enlistResource(final XAResource xaRes) {
            return false;
        }

        @Override
        public int getStatus()
                throws SystemException {
            return wrappedTransaction.getStatus();
        }

        @Override
        public void registerSynchronization(final Synchronization arg0)
                throws IllegalStateException, RollbackException, SystemException {
            wrappedTransaction.registerSynchronization(arg0);
        }

        @Override
        public void rollback()
                throws IllegalStateException, SystemException {
            wrappedTransaction.rollback();
        }

        @Override
        public void setRollbackOnly()
                throws IllegalStateException, SystemException {
            wrappedTransaction.setRollbackOnly();
        }
    }

    private class UncooperativeTransactionRegistry
            extends TransactionRegistry {

        UncooperativeTransactionRegistry(final TransactionManager transactionManager) {
            super(transactionManager);
        }

        @Override
        public TransactionContext getActiveTransactionContext() {
            try {
                return new TransactionContext(this, new UncooperativeTransaction(transactionManager.getTransaction()));
            } catch (final SystemException e) {
                return null;
            }
        }

    }

    protected PoolingDataSource<PoolableConnection> ds;

    private GenericObjectPool<PoolableConnection> pool;

    protected TransactionManager transactionManager;

    public Connection getConnection()
            throws Exception {
        return ds.getConnection();
    }

    @BeforeEach
    public void setUp()
            throws Exception {
        transactionManager = new TransactionManagerImpl();
        final Properties properties = new Properties();
        properties.setProperty(Constants.KEY_USER, "userName");
        properties.setProperty(Constants.KEY_PASSWORD, "password");
        final ConnectionFactory connectionFactory = new DriverConnectionFactory(new TesterDriver(), "jdbc:apache:commons:testdriver", properties);
        final XAConnectionFactory xaConnectionFactory = new UncooperativeLocalXAConnectionFactory(transactionManager, connectionFactory);
        final PoolableConnectionFactory factory = new PoolableConnectionFactory(xaConnectionFactory, null);
        factory.setValidationQuery("SELECT DUMMY FROM DUAL");
        factory.setDefaultReadOnly(Boolean.TRUE);
        factory.setDefaultAutoCommit(Boolean.TRUE);
        pool = new GenericObjectPool<>(factory);
        factory.setPool(pool);
        pool.setMaxTotal(10);
        pool.setMaxWaitMillis(100);
        ds = new ManagedDataSource<>(pool, xaConnectionFactory.getTransactionRegistry());
        ds.setAccessToUnderlyingConnectionAllowed(true);
    }

    @AfterEach
    public void tearDown()
            throws Exception {
        pool.close();
    }

    @Test
    public void testConnectionReturnOnErrorWhenEnlistingXAResource() throws Exception {
        transactionManager.begin();
        try {
            final DelegatingConnection<?> connectionA = (DelegatingConnection<?>) getConnection();
            connectionA.close();
        } catch (final SQLException ignored) {
        }
        transactionManager.commit();
        assertEquals(1, pool.getBorrowedCount());
        assertEquals(1, pool.getDestroyedCount());
        assertEquals(0, pool.getNumActive());
    }
}
