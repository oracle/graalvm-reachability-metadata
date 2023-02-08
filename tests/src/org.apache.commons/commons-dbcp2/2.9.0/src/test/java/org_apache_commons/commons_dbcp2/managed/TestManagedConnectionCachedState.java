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
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.dbcp2.TesterDriver;
import org.apache.commons.dbcp2.managed.LocalXAConnectionFactory;
import org.apache.commons.dbcp2.managed.ManagedDataSource;
import org.apache.commons.dbcp2.managed.XAConnectionFactory;
import org.apache.commons.pool2.SwallowedExceptionListener;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.geronimo.transaction.manager.TransactionManagerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.transaction.TransactionManager;
import javax.transaction.xa.XAException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class TestManagedConnectionCachedState {

    private static class SwallowedExceptionRecorder implements SwallowedExceptionListener {

        private final List<Exception> exceptions = new ArrayList<>();

        public List<Exception> getExceptions() {
            return exceptions;
        }

        @Override
        public void onSwallowException(final Exception e) {
            exceptions.add(e);
        }
    }

    private PoolingDataSource<PoolableConnection> ds;

    private GenericObjectPool<PoolableConnection> pool;

    private TransactionManager transactionManager;

    private SwallowedExceptionRecorder swallowedExceptionRecorder;

    public Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    @BeforeEach
    public void setUp() throws XAException {
        transactionManager = new TransactionManagerImpl();
        final Properties properties = new Properties();
        properties.setProperty(Constants.KEY_USER, "userName");
        properties.setProperty(Constants.KEY_PASSWORD, "password");
        final ConnectionFactory connectionFactory = new DriverConnectionFactory(new TesterDriver(), "jdbc:apache:commons:testdriver", properties);
        final XAConnectionFactory xaConnectionFactory = new LocalXAConnectionFactory(transactionManager, connectionFactory);
        final PoolableConnectionFactory factory = new PoolableConnectionFactory(xaConnectionFactory, null);
        factory.setValidationQuery("SELECT DUMMY FROM DUAL");
        factory.setCacheState(true);
        pool = new GenericObjectPool<>(factory);
        factory.setPool(pool);
        swallowedExceptionRecorder = new SwallowedExceptionRecorder();
        pool.setSwallowedExceptionListener(swallowedExceptionRecorder);
        ds = new ManagedDataSource<>(pool, xaConnectionFactory.getTransactionRegistry());
        ds.setAccessToUnderlyingConnectionAllowed(true);
    }

    @AfterEach
    public void tearDown() {
        pool.close();
    }

    @Test
    public void testConnectionCachedState() throws Exception {
        transactionManager.begin();
        try (Connection conn = getConnection()) {
            conn.getAutoCommit();
            transactionManager.rollback();
        }
        assertEquals(0, swallowedExceptionRecorder.getExceptions().size());
    }
}
