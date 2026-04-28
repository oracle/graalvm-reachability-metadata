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

public class StatementCacheInnerCachedStatementTest {
    private static final String SELECT_NAME_BY_ID = "SELECT name FROM cached_statement_item WHERE id = ?";

    @Test
    void closeRebuildsProxyFacadeBeforeCachingStatement() throws SQLException {
        StatementCache statementCache = new StatementCache();
        statementCache.setProperties(statementCacheProperties());
        PoolProperties poolProperties = poolProperties();
        ConnectionPool connectionPool = new ConnectionPool(poolProperties);
        PooledConnection pooledConnection = new PooledConnection(poolProperties, connectionPool);
        statementCache.poolStarted(connectionPool);
        statementCache.reset(connectionPool, pooledConnection);

        try (Connection delegate = DriverManager.getConnection(
                "jdbc:h2:mem:statement_cache_inner_cached_statement;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")) {
            createTestData(delegate);
            statementCache.setNext(new TerminalConnectionInterceptor(delegate));
            Connection connection = (Connection) Proxy.newProxyInstance(
                    StatementCacheInnerCachedStatementTest.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    statementCache);
            try {
                PreparedStatement firstStatement = connection.prepareStatement(SELECT_NAME_BY_ID);
                firstStatement.setInt(1, 1);
                assertPreparedStatementQuery(firstStatement, "first-cached-statement");

                firstStatement.close();
                assertThat(firstStatement.isClosed()).isTrue();
                assertThat(statementCache.getCacheSizePerConnection()).isEqualTo(1);

                PreparedStatement cachedStatement = connection.prepareStatement(SELECT_NAME_BY_ID);
                cachedStatement.setInt(1, 2);
                try {
                    assertThat(cachedStatement.isClosed()).isFalse();
                    assertThat(statementCache.getCacheSizePerConnection()).isZero();
                    assertPreparedStatementQuery(cachedStatement, "second-cached-statement");
                } finally {
                    cachedStatement.close();
                }
            } finally {
                connection.close();
            }
        } finally {
            statementCache.reset(null, null);
            statementCache.poolClosed(connectionPool);
        }
    }

    private Map<String, PoolProperties.InterceptorProperty> statementCacheProperties() {
        Map<String, PoolProperties.InterceptorProperty> properties = new HashMap<>();
        properties.put("prepared", new PoolProperties.InterceptorProperty("prepared", "true"));
        properties.put("callable", new PoolProperties.InterceptorProperty("callable", "false"));
        properties.put("max", new PoolProperties.InterceptorProperty("max", "4"));
        return properties;
    }

    private PoolProperties poolProperties() {
        PoolProperties poolProperties = new PoolProperties();
        poolProperties.setInitialSize(0);
        poolProperties.setMaxActive(1);
        poolProperties.setJmxEnabled(false);
        poolProperties.setTimeBetweenEvictionRunsMillis(0);
        return poolProperties;
    }

    private void createTestData(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE cached_statement_item (
                        id INT NOT NULL PRIMARY KEY,
                        name VARCHAR(255)
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO cached_statement_item(id, name)
                    VALUES (1, 'first-cached-statement')
                    """);
            statement.executeUpdate("""
                    INSERT INTO cached_statement_item(id, name)
                    VALUES (2, 'second-cached-statement')
                    """);
        }
    }

    private void assertPreparedStatementQuery(PreparedStatement statement, String expectedName) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
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
