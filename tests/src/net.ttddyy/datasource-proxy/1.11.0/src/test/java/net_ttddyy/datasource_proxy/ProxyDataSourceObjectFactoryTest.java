/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_ttddyy.datasource_proxy;

import net.ttddyy.dsproxy.QueryCountHolder;
import net.ttddyy.dsproxy.listener.DataSourceQueryCountListener;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.jndi.ProxyDataSourceObjectFactory;
import net.ttddyy.dsproxy.transform.NoOpParameterTransformer;
import net.ttddyy.dsproxy.transform.QueryTransformer;
import net.ttddyy.dsproxy.transform.TransformInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.naming.spi.InitialContextFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Hashtable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class ProxyDataSourceObjectFactoryTest {
    @Test
    void createsConfiguredDataSourceFromAJndiReference() throws Exception {
        String previousFactory = System.getProperty(Context.INITIAL_CONTEXT_FACTORY);
        QueryCountHolder.clear();
        System.setProperty(Context.INITIAL_CONTEXT_FACTORY, DatabaseContextFactory.class.getName());
        try {
            Reference reference = new Reference(ProxyDataSource.class.getName());
            reference.add(new StringRefAddr("dataSource", "jdbc/orders"));
            reference.add(new StringRefAddr("proxyName", "jndi-orders"));
            reference.add(new StringRefAddr("listeners", DataSourceQueryCountListener.class.getName()));
            reference.add(new StringRefAddr("queryTransformer", CustomerQueryTransformer.class.getName()));
            reference.add(new StringRefAddr("parameterTransformer", NoOpParameterTransformer.class.getName()));

            ProxyDataSourceObjectFactory factory = new ProxyDataSourceObjectFactory();
            try (ProxyDataSource dataSource = assertInstanceOf(ProxyDataSource.class,
                    factory.getObjectInstance(reference, null, null, null));
                 Connection connection = dataSource.getConnection();
                 PreparedStatement query = connection.prepareStatement("SELECT customer_number + CAST(? AS INTEGER)")) {
                assertEquals("jndi-orders", dataSource.getDataSourceName());
                query.setInt(1, 4);
                try (ResultSet rows = query.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(77, rows.getInt(1));
                    assertFalse(rows.next());
                }
                assertEquals(1L, QueryCountHolder.get("jndi-orders").getSelect());
                assertEquals(1L, QueryCountHolder.get("jndi-orders").getSuccess());
            }
        } finally {
            QueryCountHolder.clear();
            if (previousFactory == null) {
                System.clearProperty(Context.INITIAL_CONTEXT_FACTORY);
            } else {
                System.setProperty(Context.INITIAL_CONTEXT_FACTORY, previousFactory);
            }
        }
    }

    public static class CustomerQueryTransformer implements QueryTransformer {
        @Override
        public String transformQuery(TransformInfo info) {
            return info.getQuery().replace("customer_number", "73");
        }
    }

    public static class DatabaseContextFactory implements InitialContextFactory {
        @Override
        public Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {
            return new DatabaseContext();
        }
    }

    private static class DatabaseContext extends InitialContext {
        DatabaseContext() throws NamingException {
            super(true);
        }

        @Override
        public Object lookup(String name) throws NamingException {
            if (!"jdbc/orders".equals(name)) {
                throw new NameNotFoundException(name);
            }
            return TestDataSources.create("jndiOrders");
        }
    }
}
