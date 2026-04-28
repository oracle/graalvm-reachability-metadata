/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat.tomcat_jdbc;

import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.apache.tomcat.jdbc.pool.interceptor.StatementCache;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class StatementCacheTest {

    @Test
    void preparesStatementsThroughStatementCacheDecorator() throws SQLException {
        StatementCache statementCache = new StatementCache();
        statementCache.setProperties(statementCacheProperties());

        try (Connection delegate = DriverManager.getConnection("jdbc:h2:mem:statement_cache")) {
            createTestData(delegate);
            statementCache.setNext(new TerminalConnectionInterceptor(delegate));
            Connection connection = (Connection) Proxy.newProxyInstance(
                    StatementCacheTest.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    statementCache);
            try {
                assertPreparedStatementQuery(connection, 1, "cached-first");
                assertPreparedStatementQuery(connection, 2, "cached-second");
            } finally {
                connection.close();
            }
        }
    }

    private Map<String, PoolProperties.InterceptorProperty> statementCacheProperties() {
        Map<String, PoolProperties.InterceptorProperty> properties = new HashMap<>();
        properties.put("prepared", new PoolProperties.InterceptorProperty("prepared", "true"));
        properties.put("callable", new PoolProperties.InterceptorProperty("callable", "false"));
        properties.put("max", new PoolProperties.InterceptorProperty("max", "2"));
        return properties;
    }

    private void createTestData(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE statement_cache_item (
                        id INT NOT NULL PRIMARY KEY,
                        name VARCHAR(255)
                    )
                    """);
            statement.executeUpdate("INSERT INTO statement_cache_item(id, name) VALUES (1, 'cached-first')");
            statement.executeUpdate("INSERT INTO statement_cache_item(id, name) VALUES (2, 'cached-second')");
        }
    }

    private void assertPreparedStatementQuery(Connection connection, int id, String expectedName) throws SQLException {
        PreparedStatement statement = connection.prepareStatement("SELECT name FROM statement_cache_item WHERE id = ?");
        statement.setInt(1, id);

        try (ResultSet resultSet = statement.executeQuery()) {
            assertThat(resultSet.getStatement()).isSameAs(statement);
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString(1)).isEqualTo(expectedName);
            assertThat(resultSet.next()).isFalse();
        }
    }

    private static final class TerminalConnectionInterceptor extends JdbcInterceptor {
        private final Connection delegate;

        private TerminalConnectionInterceptor(Connection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (compare("prepareStatement", method)) {
                return delegate.prepareStatement((String) args[0]);
            }
            if (compare("close", method)) {
                delegate.close();
                return null;
            }
            if (compare("isClosed", method)) {
                return delegate.isClosed();
            }
            if (compare("toString", method)) {
                return delegate.toString();
            }
            throw new UnsupportedOperationException(method.getName());
        }

        @Override
        public void reset(ConnectionPool parent, PooledConnection con) {
            // This terminal interceptor keeps no pool state.
        }
    }
}
