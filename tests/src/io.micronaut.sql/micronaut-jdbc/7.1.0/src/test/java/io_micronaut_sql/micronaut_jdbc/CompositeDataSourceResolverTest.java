/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_sql.micronaut_jdbc;

import io.micronaut.jdbc.CompositeDataSourceResolver;
import io.micronaut.jdbc.DataSourceResolver;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompositeDataSourceResolverTest {
    @Test
    void repeatedlyUnwrapsDecoratedDataSourcesAndReturnsUsableTarget() throws Exception {
        JdbcDataSource target = new JdbcDataSource();
        target.setURL("jdbc:h2:mem:resolved_source");
        DataSource decorated = new WrappedDataSource(new WrappedDataSource(target));
        DataSourceResolver unwrap = new DataSourceResolver() {
            @Override
            public DataSource resolve(DataSource dataSource) {
                if (dataSource instanceof WrappedDataSource wrapper) {
                    return wrapper.delegate();
                }
                return dataSource;
            }
        };
        DataSourceResolver[] delegates = {DataSourceResolver.DEFAULT, unwrap};
        CompositeDataSourceResolver resolver = new CompositeDataSourceResolver(delegates);

        DataSource resolved = resolver.resolve(decorated);
        assertSame(target, resolved);
        assertSame(target, resolver.resolve(resolved));
        assertArrayEquals(delegates, resolver.getDataSourceResolvers());
        try (Connection connection = resolved.getConnection();
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            try (ResultSet result = statement.executeQuery("SELECT 6 * 7")) {
                assertTrue(result.next());
                assertEquals(42, result.getInt(1));
            }
        }
    }

    @Test
    void defaultAndEmptyResolversPreserveTheOriginalDataSource() {
        JdbcDataSource target = new JdbcDataSource();
        assertSame(target, DataSourceResolver.DEFAULT.resolve(target));
        CompositeDataSourceResolver empty = new CompositeDataSourceResolver(new DataSourceResolver[0]);
        assertSame(target, empty.resolve(target));
        CompositeDataSourceResolver identity = new CompositeDataSourceResolver(
                new DataSourceResolver[]{DataSourceResolver.DEFAULT});
        assertSame(target, identity.resolve(target));
    }

    private record WrappedDataSource(DataSource delegate) implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            return delegate.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return delegate.getConnection(username, password);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter writer) throws SQLException {
            delegate.setLogWriter(writer);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> type) throws SQLException {
            if (type.isInstance(this)) {
                return type.cast(this);
            }
            return delegate.unwrap(type);
        }

        @Override
        public boolean isWrapperFor(Class<?> type) throws SQLException {
            return type.isInstance(this) || delegate.isWrapperFor(type);
        }
    }
}
