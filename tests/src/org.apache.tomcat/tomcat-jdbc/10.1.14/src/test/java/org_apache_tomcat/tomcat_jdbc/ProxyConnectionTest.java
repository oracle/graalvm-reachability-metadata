/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat.tomcat_jdbc;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.ProxyConnection;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.XAConnection;
import javax.transaction.xa.XAResource;

import static org.assertj.core.api.Assertions.assertThat;

public class ProxyConnectionTest {

    @Test
    void delegatesXaAndJdbcMethodsToWrappedConnection() throws SQLException {
        PoolProperties poolProperties = createPoolProperties();
        ConnectionPool connectionPool = new ConnectionPool(poolProperties);
        PooledConnection pooledConnection = new PooledConnection(poolProperties, connectionPool);
        try {
            pooledConnection.connect();
            TestProxyConnection proxyConnection = new TestProxyConnection(connectionPool, pooledConnection);
            Connection connection = (Connection) Proxy.newProxyInstance(
                    ProxyConnectionTest.class.getClassLoader(),
                    new Class<?>[] {Connection.class, XAConnection.class},
                    proxyConnection);

            XAResource xaResource = ((XAConnection) connection).getXAResource();
            boolean autoCommit = connection.getAutoCommit();

            assertThat(xaResource).isNotNull();
            assertThat(autoCommit).isTrue();
        } finally {
            pooledConnection.release();
        }
    }

    private PoolProperties createPoolProperties() {
        JdbcDataSource h2DataSource = new JdbcDataSource();
        h2DataSource.setURL("jdbc:h2:mem:proxy_connection;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        h2DataSource.setUser("sa");
        h2DataSource.setPassword("");

        PoolProperties poolProperties = new PoolProperties();
        poolProperties.setDataSource(h2DataSource);
        poolProperties.setInitialSize(0);
        poolProperties.setMaxActive(1);
        poolProperties.setJmxEnabled(false);
        poolProperties.setTimeBetweenEvictionRunsMillis(0);
        return poolProperties;
    }

    private static final class TestProxyConnection extends ProxyConnection {
        private TestProxyConnection(ConnectionPool parent, PooledConnection connection) {
            super(parent, connection, true);
        }
    }
}
