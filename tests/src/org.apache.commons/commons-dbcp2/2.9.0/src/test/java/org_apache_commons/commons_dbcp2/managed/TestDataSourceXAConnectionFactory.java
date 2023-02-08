/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_dbcp2.managed;

import org.apache.commons.dbcp2.TestBasicDataSource;
import org.apache.commons.dbcp2.managed.BasicManagedDataSource;
import org.apache.commons.dbcp2.managed.TesterBasicXAConnection;
import org.apache.geronimo.transaction.manager.TransactionManagerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.XAConnection;
import javax.sql.XADataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestDataSourceXAConnectionFactory extends TestBasicDataSource {
    public class XADataSourceHandle implements InvocationHandler {
        protected XAConnection getXAConnection() throws SQLException {
            return new TesterBasicXAConnection(ds.getConnection(), closeCounter);
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args)
                throws Throwable {
            final String methodName = method.getName();
            if (methodName.equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if (methodName.equals("equals")) {
                return proxy == args[0];
            }
            if (methodName.equals("getXAConnection")) {
                return getXAConnection();
            }
            try {
                return method.invoke(ds, args);
            } catch (final InvocationTargetException e) {
                throw e.getTargetException();
            }
        }
    }

    protected BasicManagedDataSource bmds;

    public final AtomicInteger closeCounter = new AtomicInteger();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        bmds = new BasicManagedDataSource();
        bmds.setTransactionManager(new TransactionManagerImpl());
        bmds.setXADataSource("notnull");
        final XADataSourceHandle handle = new XADataSourceHandle();
        final XADataSource xads = (XADataSource) Proxy.newProxyInstance(XADataSourceHandle.class.getClassLoader(),
                new Class[]{XADataSource.class}, handle);
        bmds.setXaDataSourceInstance(xads);
    }

    @Test
    public void testPhysicalClose() throws Exception {
        bmds.setMaxIdle(1);
        final Connection conn1 = bmds.getConnection();
        final Connection conn2 = bmds.getConnection();
        closeCounter.set(0);
        conn1.close();
        assertEquals(0, closeCounter.get());
        conn2.close();
        assertEquals(1, closeCounter.get());
        bmds.close();
        assertEquals(2, closeCounter.get());
    }
}
